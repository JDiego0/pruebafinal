package co.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One copilot turn persisted for usage ledger and audit. */
public record CopilotTurn(
        UUID turnId,
        UUID threadId,
        UserId userId,
        String question,
        String status,        // ANSWERED | INSUFFICIENT_CONTEXT | NO_PERMISSION | OUT_OF_SCOPE | PROVIDER_ERROR
        String answer,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String promptVersion,
        Instant createdAt
) {}
