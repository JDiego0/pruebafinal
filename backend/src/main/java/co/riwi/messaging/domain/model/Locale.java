package co.riwi.messaging.domain.model;

import co.riwi.messaging.domain.error.ValidationError;

/** Mirrors rw_users_locale_ck (FE-14, FE-15). */
public enum Locale {
    ES, EN;

    public static Locale fromCode(String code) {
        if (code == null) {
            throw new ValidationError("locale", "must not be null");
        }
        return switch (code.toLowerCase()) {
            case "es" -> ES;
            case "en" -> EN;
            default -> throw new ValidationError("locale", "must be one of: es, en");
        };
    }

    public String code() {
        return name().toLowerCase();
    }
}
