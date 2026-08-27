package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** AI-11, AI-12, AI-15: persist copilot threads, turns, citations and the usage ledger. */
public interface CopilotRepository {

    record TurnResult(UUID turnId, UUID threadId) {}

    /** One validated citation: the message, how close it was, and its position in the answer. */
    record CitationRow(MessageId messageId, double similarity, int rank) {}

    /**
     * Records a completed turn, creating the conversation thread when
     * {@code threadId} is null. The thread is always owned by {@code userId} —
     * the RLS WITH CHECK policy refuses any other owner.
     */
    TurnResult recordTurn(
            UserId userId,
            UUID threadId,
            ChannelId channelId,
            short systemPromptId,
            String question,
            String answerStatus,
            String answer,
            int candidateCount
    );

    /** Records the citation links for a turn. Every id must already be authorized. */
    void recordCitations(UUID turnId, List<CitationRow> citations);

    /** AI-15: appends one row to the per-user consumption ledger. */
    void recordUsage(
            UserId userId,
            UUID turnId,
            String providerCode,
            String modelCode,
            String operation,
            int promptTokens,
            int completionTokens,
            int latencyMs
    );

    record UsageSummary(
            String userId,
            long totalRequests,
            long totalPromptTokens,
            long totalCompletionTokens,
            long totalTokens,
            Instant firstUsedAt,
            Instant lastUsedAt
    ) {}

    /** OBJ-07: accumulated copilot consumption for the authenticated user. */
    UsageSummary usageSummary(UserId userId);
}
