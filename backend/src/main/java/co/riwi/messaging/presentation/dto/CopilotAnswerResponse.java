package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.application.usecase.AskCopilotUseCase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CopilotAnswerResponse(
        UUID turnId,
        UUID threadId,
        String status,
        String answer,
        List<CitationResponse> citations,
        String promptVersion,
        TokenUsage usage
) {
    public record CitationResponse(
            long messageId,
            String channelId,
            String senderDisplayName,
            Instant createdAt,
            String excerpt,
            double similarity,
            int rank
    ) {}

    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {}

    public static CopilotAnswerResponse from(AskCopilotUseCase.AskResult r) {
        List<CitationResponse> cites = r.citations().stream().map(c ->
                new CitationResponse(
                        c.messageId().value(),
                        c.channelId().value().toString(),
                        c.senderDisplayName(),
                        c.createdAt(),
                        c.excerpt(),
                        c.similarity(),
                        c.rank()
                )
        ).toList();

        return new CopilotAnswerResponse(
                r.turnId(), r.threadId(), r.status().name(), r.answer(),
                cites, r.promptVersion(),
                new TokenUsage(r.promptTokens(), r.completionTokens(), r.totalTokens())
        );
    }
}
