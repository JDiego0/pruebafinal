package co.riwi.messaging.domain.model;

import co.riwi.messaging.domain.error.ValidationError;

import java.util.regex.Pattern;

/**
 * Validates at construction (ARCH-05: use cases stay thin because the type
 * cannot hold an invalid value). Mirrors the database CHECK constraint on
 * rw_users.rw_email so the two layers cannot silently disagree.
 */
public record Email(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new ValidationError("email", "must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        if (!SHAPE.matcher(normalized).matches()) {
            throw new ValidationError("email", "must be a valid email address");
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
