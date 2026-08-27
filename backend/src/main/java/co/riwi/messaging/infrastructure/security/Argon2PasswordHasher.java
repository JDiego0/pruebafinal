package co.riwi.messaging.infrastructure.security;

import co.riwi.messaging.domain.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * D-19: Argon2id via Spring Security Crypto, current OWASP recommendation —
 * memory-hard (unlike bcrypt/PBKDF2), no 72-byte truncation. Parameters
 * follow the library's vetted defaults (19 MiB memory, 2 iterations, 1
 * thread), matching the seed data's stored hash format so seeded users can
 * log in unmodified.
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        return encoder.matches(rawPassword, encodedHash);
    }
}
