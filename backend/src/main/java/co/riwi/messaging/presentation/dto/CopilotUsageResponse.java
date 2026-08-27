package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.port.CopilotRepository;

import java.time.Instant;

public record CopilotUsageResponse(
        String userId,
        long totalRequests,
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalTokens,
        Instant firstUsedAt,
        Instant lastUsedAt
) {
    public static CopilotUsageResponse from(CopilotRepository.UsageSummary u) {
        return new CopilotUsageResponse(
                u.userId(), u.totalRequests(),
                u.totalPromptTokens(), u.totalCompletionTokens(), u.totalTokens(),
                u.firstUsedAt(), u.lastUsedAt()
        );
    }
}
