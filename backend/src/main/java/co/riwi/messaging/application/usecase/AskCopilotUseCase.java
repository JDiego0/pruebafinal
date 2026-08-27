package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.*;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI-01..AI-15: full RAG copilot pipeline.
 *
 * Pipeline:
 *  1. Embed the question (EmbeddingProvider)
 *  2. Retrieve authorized candidates (RagRepository — SECURITY INVOKER under actor's RLS)
 *  3. If no candidates → status = INSUFFICIENT_CONTEXT (AI-04)
 *  4. Build context block from ranked candidates (AI-10)
 *  5. Load system prompt (AI-14)
 *  6. Call AI provider (AIProvider)
 *  7. Parse [msg:<id>] citations from answer and validate them against the candidate list (AI-11)
 *  8. Persist turn + citations (AI-12, AI-15)
 *  9. Return AskResult
 *
 * Security invariants:
 *  - The actor's userId comes from the JWT (never from the request body)
 *  - RAG retrieval runs inside the same RLS transaction as the user (AI-08, SEC-06)
 *  - Citations are validated against the authorized candidate set — the model cannot
 *    cite a message the user cannot read (AI-11, SEC-08)
 *  - Untrusted context is delimiter-wrapped in ChatRequest (AI-09)
 */
public class AskCopilotUseCase {

    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[msg:(\\d+)]");
    private static final int MAX_ANSWER_TOKENS = 1024;

    private final AIProvider aiProvider;
    private final EmbeddingProvider embeddingProvider;
    private final RagRepository ragRepository;
    private final CopilotRepository copilotRepository;
    private final SystemPromptRepository systemPromptRepository;
    private final TransactionRunner transactionRunner;
    private final int candidateLimit;
    private final double similarityFloor;

    public AskCopilotUseCase(AIProvider aiProvider, EmbeddingProvider embeddingProvider,
                               RagRepository ragRepository, CopilotRepository copilotRepository,
                               SystemPromptRepository systemPromptRepository,
                               TransactionRunner transactionRunner,
                               int candidateLimit, double similarityFloor) {
        this.aiProvider = aiProvider;
        this.embeddingProvider = embeddingProvider;
        this.ragRepository = ragRepository;
        this.copilotRepository = copilotRepository;
        this.systemPromptRepository = systemPromptRepository;
        this.transactionRunner = transactionRunner;
        this.candidateLimit = candidateLimit;
        this.similarityFloor = similarityFloor;
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public enum Status { ANSWERED, INSUFFICIENT_CONTEXT, NO_PERMISSION, OUT_OF_SCOPE, PROVIDER_ERROR }

    public record Citation(MessageId messageId, ChannelId channelId, String channelName,
                           String senderDisplayName, java.time.Instant createdAt,
                           String excerpt, double similarity, int rank) {}

    public record AskResult(UUID turnId, UUID threadId, Status status, String answer,
                             List<Citation> citations, String promptVersion,
                             int promptTokens, int completionTokens, int totalTokens) {}

    // ── Command ───────────────────────────────────────────────────────────────

    public record AskCommand(UserId actorId, String question, ChannelId channelId, UUID threadId) {}

    // ── Execute ───────────────────────────────────────────────────────────────

    public AskResult execute(AskCommand cmd) {
        // Validate question length (AI-10 — prevent prompt-bomb)
        String question = cmd.question().trim();
        if (question.length() > 1000) {
            question = question.substring(0, 1000);
        }

        // Provider availability check (AI-03)
        if (!aiProvider.isEnabled()) {
            return persistAndReturn(cmd, Status.PROVIDER_ERROR, null, List.of(),
                    "none", "0.0.0", 0, 0);
        }

        // 1. Embed question
        float[] queryVector;
        try {
            queryVector = embeddingProvider.embed(List.of(question)).get(0);
        } catch (Exception e) {
            return persistAndReturn(cmd, Status.PROVIDER_ERROR, null, List.of(),
                    "none", "0.0.0", 0, 0);
        }

        // 2. Retrieve authorized candidates (RLS-scoped)
        final String finalQuestion = question;
        final float[] finalVector = queryVector;

        List<RagCandidate> candidates = transactionRunner.runAsActor(cmd.actorId(), () ->
                ragRepository.retrieveCandidates(finalVector, cmd.channelId(),
                        candidateLimit, similarityFloor)
        );

        // 3. Insufficient context check (AI-04)
        if (candidates.isEmpty()) {
            return persistAndReturn(cmd, Status.INSUFFICIENT_CONTEXT, null, List.of(),
                    "none", "0.0.0", 0, 0);
        }

        // 4. Build context block (AI-10)
        String contextBlock = buildContextBlock(candidates);

        // 5. Load system prompt (AI-14)
        SystemPromptRepository.ActivePrompt prompt = systemPromptRepository.active();

        // 6. Identity block — server-built from JWT actor, never client-provided (AI-13)
        String identityBlock = "User-ID: " + cmd.actorId().value() + " | Channel: "
                + (cmd.channelId() != null ? cmd.channelId().value() : "global");

        // 7. Call AI provider
        ChatRequest chatReq = ChatRequest.of(prompt.promptText(), identityBlock,
                contextBlock, finalQuestion, MAX_ANSWER_TOKENS);
        ChatResponse chatRes;
        try {
            chatRes = aiProvider.chat(chatReq);
        } catch (Exception e) {
            return persistAndReturn(cmd, Status.PROVIDER_ERROR, null, List.of(),
                    "none", prompt.version(), 0, 0);
        }

        // 8. Validate citations (AI-11, SEC-08) — model can only cite authorized messages
        List<Citation> citations = parseCitations(chatRes.content(), candidates);

        // 9. Persist and return
        return persistAndReturn(cmd, Status.ANSWERED, chatRes.content(), citations,
                prompt.version(), prompt.version(),
                chatRes.promptTokens(), chatRes.completionTokens());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildContextBlock(List<RagCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        int chars = 0;
        for (int i = 0; i < candidates.size(); i++) {
            RagCandidate c = candidates.get(i);
            String excerpt = truncate(c.body(), 500);
            String line = String.format("[msg:%d] %s — %s: %s\n",
                    c.messageId().value(), c.createdAt(), c.senderDisplayName(), excerpt);
            if (chars + line.length() > MAX_CONTEXT_CHARS) break;
            sb.append(line);
            chars += line.length();
        }
        return sb.toString();
    }

    /**
     * AI-11: parse [msg:<id>] inline citations from the answer text,
     * then filter to the authorized candidate set only.
     * Any citation referencing a message_id NOT in the candidates list is silently dropped
     * (SEC-08 — the model cannot exfiltrate information about unauthorized messages).
     */
    private List<Citation> parseCitations(String answer, List<RagCandidate> candidates) {
        if (answer == null || answer.isBlank()) return List.of();

        Matcher m = CITATION_PATTERN.matcher(answer);
        return m.results()
                .map(r -> Long.parseLong(r.group(1)))
                .distinct()
                .flatMap(msgId -> candidates.stream()
                        .filter(c -> c.messageId().value() == msgId)
                        .findFirst()
                        .stream())
                .map(c -> new Citation(c.messageId(), c.channelId(), null,
                        c.senderDisplayName(), c.createdAt(),
                        truncate(c.body(), 200), c.similarity(), 0))
                .collect(Collectors.toList());
    }

    private AskResult persistAndReturn(AskCommand cmd, Status status, String answer,
                                        List<Citation> citations, String providerCode,
                                        String promptVersion, int promptTokens, int completionTokens) {
        CopilotRepository.TurnResult turn = copilotRepository.recordTurn(
                cmd.actorId(), cmd.threadId(), cmd.question(),
                status.name(), answer, promptVersion,
                promptTokens, completionTokens, providerCode, "unknown"
        );

        if (!citations.isEmpty()) {
            List<MessageId> citedIds = citations.stream().map(Citation::messageId).toList();
            copilotRepository.recordCitations(turn.turnId(), citedIds);
        }

        return new AskResult(turn.turnId(), turn.threadId(), status, answer, citations,
                promptVersion, promptTokens, completionTokens, promptTokens + completionTokens);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }
}
