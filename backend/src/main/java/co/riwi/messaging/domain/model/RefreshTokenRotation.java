package co.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Result of rw_fn_rotate_refresh_token. {@code status} drives the use case's branch. */
public record RefreshTokenRotation(
        RotationStatus status,
        UserId userId,
        UUID familyId,
        UUID newRefreshTokenId,
        Instant expiresAt
) {
    public enum RotationStatus { ROTATED, REUSE_DETECTED, EXPIRED, NOT_FOUND }
}
