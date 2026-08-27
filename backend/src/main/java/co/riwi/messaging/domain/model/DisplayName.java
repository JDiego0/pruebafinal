package co.riwi.messaging.domain.model;

import co.riwi.messaging.domain.error.ValidationError;

public record DisplayName(String value) {

    public DisplayName {
        if (value == null) {
            throw new ValidationError("displayName", "must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.length() < 2 || trimmed.length() > 80) {
            throw new ValidationError("displayName", "must be between 2 and 80 characters");
        }
        value = trimmed;
    }

    public static DisplayName of(String value) {
        return new DisplayName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
