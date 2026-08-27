package co.riwi.messaging.domain.model;

/** Result of rw_fn_get_credential_for_login — internal only, never serialized to a client. */
public record CredentialLookup(
        UserId userId,
        String passwordHash,
        int failedAttempts,
        java.time.Instant lockedUntil,
        boolean active
) {
}
