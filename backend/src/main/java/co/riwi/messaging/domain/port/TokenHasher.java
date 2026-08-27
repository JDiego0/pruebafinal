package co.riwi.messaging.domain.port;

/**
 * SHA-256 of an opaque refresh token, so only the hash is ever persisted
 * (AUTH-04). Not for passwords — those use Argon2id via a dedicated
 * infrastructure component, never this port.
 */
public interface TokenHasher {
    byte[] hash(String rawToken);

    String generateOpaqueToken();
}
