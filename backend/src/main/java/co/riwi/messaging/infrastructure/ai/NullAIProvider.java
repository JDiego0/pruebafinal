package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.model.ChatRequest;
import co.riwi.messaging.domain.model.ChatResponse;
import co.riwi.messaging.domain.port.AIProvider;

/**
 * NullAIProvider — returned when AI_ENABLED=false (the default).
 * Keeps the stack fully operational with no credentials. The use case
 * detects isEnabled()=false and returns PROVIDER_UNAVAILABLE without
 * attempting a network call (AI-03, D-21).
 */
public class NullAIProvider implements AIProvider {

    @Override
    public ChatResponse chat(ChatRequest request) {
        throw new UnsupportedOperationException("AI provider is not enabled");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
