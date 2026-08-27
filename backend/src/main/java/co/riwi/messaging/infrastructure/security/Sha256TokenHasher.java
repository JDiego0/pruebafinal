package co.riwi.messaging.infrastructure.security;

import co.riwi.messaging.domain.port.TokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AUTH-04: the refresh token stored at rest is a SHA-256 hash, never the
 * plaintext token. The opaque token itself is 256 bits of {@link SecureRandom}.
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public byte[] hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }

    @Override
    public String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
