package co.riwi.messaging.domain.error;

/**
 * Base of every domain-level failure. Presentation maps each subtype to
 * exactly one HTTP status in one place (the {@code @RestControllerAdvice}) —
 * see docs/API.md §1 and ARCHITECTURE.md §3.5. Never let a raw exception
 * (SQLException, NullPointerException, ...) reach the client; wrap or let it
 * fall through to the advice's generic 500 handler, which never leaks detail.
 */
public abstract class DomainError extends RuntimeException {
    protected DomainError(String message) {
        super(message);
    }
}
