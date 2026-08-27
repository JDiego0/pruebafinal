package co.riwi.messaging.domain.model;

import co.riwi.messaging.domain.error.ValidationError;

/**
 * Mirrors rw_messages_deleted_shape_ck's live-message branch: 1-4000 chars
 * after trimming. A MessageBody instance can never represent an empty or
 * oversized message, so use cases never need to re-check this.
 */
public record MessageBody(String value) {

    public static final int MAX_LENGTH = 4000;

    public MessageBody {
        if (value == null) {
            throw new ValidationError("body", "must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationError("body", "must not be blank");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new ValidationError("body", "must be at most " + MAX_LENGTH + " characters");
        }
    }

    public static MessageBody of(String value) {
        return new MessageBody(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
