package co.riwi.messaging.infrastructure.security;

import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.Role;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.AccessTokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * AUTH-02/AUTH-03: HMAC-signed access tokens, 15-minute default lifetime.
 * The exact algorithm follows the key length JWT_SECRET supplies — HS256,
 * HS384 or HS512 — and a secret too short for any of them is rejected at
 * startup rather than silently weakening the signature. The
 * token carries claims for convenience only — anything security-relevant
 * (role, active status) is re-read from the database by whichever use case
 * needs it; the claim is never trusted as the sole source of truth for an
 * authorization decision beyond "this request is authenticated as this user".
 */
@Component
public class JwtService implements AccessTokenPort {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final long accessTokenTtlSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret must be configured (see .env.example JWT_SECRET)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @Override
    public String issue(UserId userId, Role role, DisplayName displayName, Locale locale) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.value().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtlSeconds, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())
                .claim("role", role.code())
                .claim("name", displayName.value())
                .claim("locale", locale.code())
                .signWith(key)
                .compact();
    }

    @Override
    public long ttlSeconds() {
        return accessTokenTtlSeconds;
    }

    /** @throws JwtException if the token is malformed, expired, or fails signature verification. */
    public VerifiedAccessToken verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new VerifiedAccessToken(
                UserId.of(UUID.fromString(claims.getSubject())),
                Role.fromCode(claims.get("role", String.class)),
                DisplayName.of(claims.get("name", String.class)),
                Locale.fromCode(claims.get("locale", String.class))
        );
    }

    public record VerifiedAccessToken(UserId userId, Role role, DisplayName displayName, Locale locale) {
    }
}
