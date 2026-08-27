package co.riwi.messaging.domain.error;

/** A single field failed a value-object invariant. Maps to 400/422. */
public class ValidationError extends DomainError {
    private final String field;

    public ValidationError(String field, String reason) {
        super(field + ": " + reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
