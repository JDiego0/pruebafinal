package co.riwi.messaging.domain.model;

/**
 * AI-02: provider-agnostic chat response.
 * The infrastructure adapter translates the vendor response into this.
 */
public record ChatResponse(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {}
