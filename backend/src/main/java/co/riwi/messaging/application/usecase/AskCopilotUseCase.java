package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChatRequest;
import co.riwi.messaging.domain.model.ChatResponse;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.RagCandidate;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.AIProvider;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.CopilotRepository;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import co.riwi.messaging.domain.port.RagRepository;
import co.riwi.messaging.domain.port.SystemPromptRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-01..AI-15: the copilot pipeline.
 *
 * <pre>
 *   authenticated actor
 *     -> embed the question
 *     -> permission-aware retrieval (RLS-scoped, inside the actor's transaction)
 *     -> authorized context only
 *     -> LLM
 *     -> citations intersected back with the retrieved set
 * </pre>
 *
 * The forbidden shape — global vector search followed by application filtering —
 * is not expressible here: the only retrieval call is
 * {@link RagRepository#retrieveCandidates}, which runs inside
 * {@link TransactionRunner#runAsActor} and reaches an ANN operator whose FROM
 * clause is already constrained by RLS plus an explicit membership check.
 */
public class AskCopilotUseCase {

    /**
     * Matches the citation marker regardless of how the model bracketed it.
     * Models routinely emit fullwidth brackets or parentheses instead of the
     * requested {@code [msg:12]}, and a stricter pattern silently dropped
     * perfectly good citations — which then read as an ungrounded answer.
     * Being permissive here is safe: what makes a citation legitimate is the
     * intersection with the retrieved candidate set, not its punctuation.
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile("msg\\s*:\\s*(\\d+)");
    private static final int MAX_QUESTION_CHARS = 1000;
    private static final int MAX_ANSWER_TOKENS = 1024;
    private static final int MAX_EXCERPT_CHARS = 500;

    private final AIProvider aiProvider;
    private final EmbeddingProvider embeddingProvider;
    private final RagRepository ragRepository;
    private final ChannelRepository channelRepository;
    private final CopilotRepository copilotRepository;
    private final SystemPromptRepository systemPromptRepository;
    private final TransactionRunner transactionRunner;
    private final int candidateLimit;
    private final double similarityFloor;
    private final int maxContextChars;
    private final String providerCode;
    private final String modelCode;

    public AskCopilotUseCase(AIProvider aiProvider, EmbeddingProvider embeddingProvider,
                             RagRepository ragRepository, ChannelRepository channelRepository,
                             CopilotRepository copilotRepository,
                             SystemPromptRepository systemPromptRepository,
                             TransactionRunner transactionRunner,
                             int candidateLimit, double similarityFloor, int maxContextChars,
                             String providerCode, String modelCode) {
        this.aiProvider = aiProvider;
        this.embeddingProvider = embeddingProvider;
        this.ragRepository = ragRepository;
        this.channelRepository = channelRepository;
        this.copilotRepository = copilotRepository;
        this.systemPromptRepository = systemPromptRepository;
        this.transactionRunner = transactionRunner;
        this.candidateLimit = candidateLimit;
        this.similarityFloor = similarityFloor;
        this.maxContextChars = maxContextChars;
        this.providerCode = providerCode;
        this.modelCode = modelCode;
    }

    // ── Contract ─────────────────────────────────────────────────────────────

    /** Mirrors rw_copilot_turns_status_ck — the database is the source of truth. */
    public enum Status { ANSWERED, INSUFFICIENT_CONTEXT, NO_PERMISSION, OUT_OF_SCOPE, PROVIDER_ERROR }

    public record Citation(MessageId messageId, ChannelId channelId, String senderDisplayName,
                           Instant createdAt, String excerpt, double similarity, int rank) {}

    public record AskResult(UUID turnId, UUID threadId, Status status, String answer,
                            List<Citation> citations, String promptVersion,
                            int promptTokens, int completionTokens, int totalTokens) {}

    public record AskCommand(UserId actorId, String question, ChannelId channelId, UUID threadId) {}

    // ── Execute ──────────────────────────────────────────────────────────────

    public AskResult execute(AskCommand cmd) {
        String question = cmd.question().trim();
        if (question.length() > MAX_QUESTION_CHARS) {
            question = question.substring(0, MAX_QUESTION_CHARS);
        }

        SystemPromptRepository.ActivePrompt prompt =
                transactionRunner.runAsActor(cmd.actorId(), systemPromptRepository::active);

        // A question scoped to a channel the actor cannot read is refused before
        // any retrieval happens, and refused as NO_PERMISSION rather than as an
        // empty answer, so the user knows the scope was the problem (AI-04).
        if (cmd.channelId() != null) {
            boolean member = transactionRunner.runAsActor(cmd.actorId(),
                    () -> channelRepository.isMember(cmd.channelId()));
            if (!member) {
                return persist(cmd, prompt, Status.NO_PERMISSION, null, List.of(), 0, 0, 0, 0);
            }
        }

        if (!aiProvider.isEnabled() || !embeddingProvider.isEnabled()) {
            return persist(cmd, prompt, Status.PROVIDER_ERROR, null, List.of(), 0, 0, 0, 0);
        }

        float[] queryVector;
        try {
            queryVector = embeddingProvider.embed(List.of(question)).get(0);
        } catch (RuntimeException e) {
            return persist(cmd, prompt, Status.PROVIDER_ERROR, null, List.of(), 0, 0, 0, 0);
        }

        // The single retrieval call. Runs as the actor; nothing unauthorized can come back.
        List<RagCandidate> candidates = transactionRunner.runAsActor(cmd.actorId(),
                () -> ragRepository.retrieveCandidates(queryVector, cmd.channelId(),
                        candidateLimit, similarityFloor));

        if (candidates.isEmpty()) {
            return persist(cmd, prompt, Status.INSUFFICIENT_CONTEXT, null, List.of(), 0, 0, 0, 0);
        }

        String identityBlock = "user-id: " + cmd.actorId().value()
                + "\nchannel-scope: " + (cmd.channelId() != null ? cmd.channelId().value() : "all authorized channels");

        ChatRequest request = ChatRequest.of(prompt.promptText(), identityBlock,
                buildContextBlock(candidates), question, MAX_ANSWER_TOKENS);

        long startedAt = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = aiProvider.chat(request);
        } catch (RuntimeException e) {
            return persist(cmd, prompt, Status.PROVIDER_ERROR, null, List.of(), 0, 0, 0,
                    (int) (System.currentTimeMillis() - startedAt));
        }
        int latencyMs = (int) (System.currentTimeMillis() - startedAt);

        List<Citation> citations = validateCitations(response.content(), candidates);

        // Authorized context was available but the answer grounds itself in none
        // of it: the question was about something outside these conversations.
        Status status = citations.isEmpty() ? Status.OUT_OF_SCOPE : Status.ANSWERED;
        return persist(cmd, prompt, status, response.content(), citations,
                response.promptTokens(), response.completionTokens(), candidates.size(), latencyMs);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildContextBlock(List<RagCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        for (RagCandidate c : candidates) {
            String line = "[msg:" + c.messageId().value() + "] " + c.createdAt() + " — "
                    + (c.senderDisplayName() != null ? c.senderDisplayName() : "unknown") + ": "
                    + truncate(c.body(), MAX_EXCERPT_CHARS) + "\n";
            if (sb.length() + line.length() > maxContextChars) {
                break;
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * AI-11, SEC-08: the answer may only cite what retrieval actually returned.
     * Any [msg:id] the model invented, or lifted from injected text inside a
     * message body, is dropped here — it never reaches the client, so the model
     * cannot confirm the existence of a message the actor may not read.
     */
    private List<Citation> validateCitations(String answer, List<RagCandidate> candidates) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        List<Citation> citations = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        List<Long> seen = new ArrayList<>();
        while (matcher.find()) {
            long id = Long.parseLong(matcher.group(1));
            if (seen.contains(id)) {
                continue;
            }
            seen.add(id);
            candidates.stream()
                    .filter(c -> c.messageId().value() == id)
                    .findFirst()
                    .ifPresent(c -> citations.add(new Citation(
                            c.messageId(), c.channelId(), c.senderDisplayName(), c.createdAt(),
                            truncate(c.body(), 200), c.similarity(), citations.size() + 1)));
        }
        return citations;
    }

    private AskResult persist(AskCommand cmd, SystemPromptRepository.ActivePrompt prompt,
                              Status status, String answer, List<Citation> citations,
                              int promptTokens, int completionTokens, int candidateCount, int latencyMs) {
        CopilotRepository.TurnResult turn = transactionRunner.runAsActor(cmd.actorId(), () -> {
            CopilotRepository.TurnResult result = copilotRepository.recordTurn(
                    cmd.actorId(), cmd.threadId(), cmd.channelId(), prompt.id(),
                    cmd.question(), status.name(), answer, candidateCount);
            if (!citations.isEmpty()) {
                copilotRepository.recordCitations(result.turnId(), citations.stream()
                        .map(c -> new CopilotRepository.CitationRow(c.messageId(), c.similarity(), c.rank()))
                        .toList());
            }
            if (promptTokens > 0 || completionTokens > 0) {
                // 'CHAT' matches rw_copilot_usage_operation_ck; the database owns the vocabulary.
                copilotRepository.recordUsage(cmd.actorId(), result.turnId(), providerCode, modelCode,
                        "CHAT", promptTokens, completionTokens, latencyMs);
            }
            return result;
        });

        return new AskResult(turn.turnId(), turn.threadId(), status, answer, citations,
                prompt.version(), promptTokens, completionTokens, promptTokens + completionTokens);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
