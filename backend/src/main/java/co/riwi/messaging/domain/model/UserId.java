package co.riwi.messaging.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A user's identity. Never constructed from an untrusted request parameter for
 * "who is acting" purposes — the acting user's id comes only from
 * {@code AuthenticatedActor}, itself built from a verified JWT (AUTH-09).
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "user id must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
