package co.riwi.messaging.domain.error;

/** The configured AI provider is unreachable or not configured. Maps to 503. */
public class ProviderUnavailable extends DomainError {
    public ProviderUnavailable(String message) {
        super(message);
    }
}
