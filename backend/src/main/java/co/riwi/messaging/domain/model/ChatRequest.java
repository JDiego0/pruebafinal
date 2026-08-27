package co.riwi.messaging.domain.model;

import java.util.List;

/**
 * AI-02: provider-agnostic chat request. The application layer builds this;
 * the infrastructure adapter translates it to the vendor's wire format.
 * No vendor import reaches the domain.
 */
public record ChatRequest(
        String systemPrompt,
        /** Server-built identity block (AI-13 — injected here, never from the client). */
        String identityBlock,
        /** Untrusted context window: pre-sanitized message excerpts (AI-09). */
        String contextBlock,
        String question,
        int maxTokens
) {
    public static ChatRequest of(String systemPrompt, String identityBlock,
                                  String contextBlock, String question, int maxTokens) {
        return new ChatRequest(systemPrompt, identityBlock, contextBlock, question, maxTokens);
    }
}
