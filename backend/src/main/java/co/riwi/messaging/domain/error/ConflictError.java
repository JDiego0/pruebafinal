package co.riwi.messaging.domain.error;

/** A state conflict (e.g. removing the last channel owner). Maps to 409. */
public class ConflictError extends DomainError {
    public ConflictError(String message) {
        super(message);
    }
}
