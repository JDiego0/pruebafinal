package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.RefreshTokenRotation;
import co.riwi.messaging.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * The only path to rw_refresh_tokens, which rw_app has zero table grant on
 * (D-16). Every method reaches a SECURITY DEFINER function; authorization
 * for rotate/revoke comes from possessing the actual token (proven by its
 * hash), never from a trusted user id (AUTH-05, AUTH-06).
 */
public interface RefreshTokenPort {

    UUID issue(UserId userId, UUID familyId, byte[] tokenHash, Instant expiresAt, String userAgent, String ip);

    RefreshTokenRotation rotate(byte[] presentedTokenHash, byte[] newTokenHash, Instant newExpiresAt, String userAgent, String ip);

    void revokeFamily(UUID familyId, String reason);

    /** @return true if a live token/family was found and revoked; false if the token was unknown or already revoked. */
    boolean revokeByTokenHash(byte[] tokenHash, String reason);
}
