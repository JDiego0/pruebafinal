package co.riwi.messaging.domain.model;

/**
 * System-wide role (rw_roles). Governs administrative capability only —
 * NEVER message-content access (IBR-01). Nothing in this codebase may branch
 * on {@code Role.ADMIN} to grant a content read that RLS would otherwise
 * deny; that would violate the absolute security requirement.
 */
public enum Role {
    ADMIN, MEMBER;

    public static Role fromCode(String code) {
        return valueOf(code.toUpperCase());
    }

    public String code() {
        return name();
    }
}
