package co.riwi.messaging.domain.port;

/**
 * AUTH-01: memory-hard hashing (Argon2id in the infrastructure adapter,
 * D-19). PostgreSQL has no Argon2 support, so verification happens here in
 * application code, never in SQL — this is why {@code rw_fn_get_credential_for_login}
 * (V4) returns the stored hash to be checked against this port instead of
 * returning a boolean itself.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedHash);
}
