package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.*;

import java.util.List;
import java.util.UUID;

/** AI-11, AI-12, AI-15: persist copilot turns, citations, and usage ledger. */
public interface CopilotRepository {

    record TurnResult(UUID turnId, UUID threadId) {}

    /** Record a completed turn with its usage and return the generated ids. */
    TurnResult recordTurn(
            UserId userId,
            UUID threadId,
            String question,
            String status,
            String answer,
            String promptVersion,
            int promptTokens,
            int completionTokens,
            String providerCode,
            String modelCode
    );

    /** Record citation links for a successfully answered turn. */
    void recordCitations(UUID turnId, List<MessageId> citedMessageIds);

    record UsageSummary(
            String userId,
            long totalRequests,
            long totalPromptTokens,
            long totalCompletionTokens,
            long totalTokens,
            java.time.Instant firstUsedAt,
            java.time.Instant lastUsedAt
    ) {}

    /** OBJ-07: accumulated copilot consumption for the authenticated user. */
    UsageSummary usageSummary(UserId userId);
}
