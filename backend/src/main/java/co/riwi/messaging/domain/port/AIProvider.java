package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChatRequest;
import co.riwi.messaging.domain.model.ChatResponse;

/**
 * AI-02: strategy port for chat completion.
 * Implementation is swapped by configuration — the use case never names a vendor.
 */
public interface AIProvider {
    ChatResponse chat(ChatRequest request);

    /** Returns true if the provider is configured and reachable (startup validation). */
    boolean isEnabled();
}
