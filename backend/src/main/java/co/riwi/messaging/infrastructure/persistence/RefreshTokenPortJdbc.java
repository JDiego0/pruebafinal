package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.RefreshTokenRotation;
import co.riwi.messaging.domain.model.RefreshTokenRotation.RotationStatus;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.RefreshTokenPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Calls the SECURITY DEFINER refresh-token functions from V4 — the only path
 * to rw_refresh_tokens (D-16). Rotation authorizes itself by the presented
 * token's hash, never by a trusted user id (AUTH-05, AUTH-06).
 */
@Repository
public class RefreshTokenPortJdbc implements RefreshTokenPort {

    private final JdbcTemplate jdbc;

    public RefreshTokenPortJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID issue(UserId userId, UUID familyId, byte[] tokenHash, Instant expiresAt, String userAgent, String ip) {
        return jdbc.queryForObject(
                "SELECT rw.rw_fn_issue_refresh_token(?, ?, ?, ?, ?, ?::inet)",
                UUID.class,
                userId.value(), familyId, tokenHash, Timestamp.from(expiresAt), userAgent, ip
        );
    }

    @Override
    public RefreshTokenRotation rotate(byte[] presentedTokenHash, byte[] newTokenHash, Instant newExpiresAt, String userAgent, String ip) {
        return jdbc.queryForObject(
                "SELECT rw_status, rw_user_id, rw_family_id, rw_new_refresh_token_id, rw_expires_at " +
                        "FROM rw.rw_fn_rotate_refresh_token(?, ?, ?, ?, ?::inet)",
                (rs, rowNum) -> {
                    RotationStatus status = RotationStatus.valueOf(rs.getString("rw_status"));
                    UUID userId = (UUID) rs.getObject("rw_user_id");
                    UUID familyId = (UUID) rs.getObject("rw_family_id");
                    UUID newTokenId = (UUID) rs.getObject("rw_new_refresh_token_id");
                    Timestamp expiresAt = rs.getTimestamp("rw_expires_at");
                    return new RefreshTokenRotation(
                            status,
                            userId == null ? null : UserId.of(userId),
                            familyId,
                            newTokenId,
                            expiresAt == null ? null : expiresAt.toInstant()
                    );
                },
                presentedTokenHash, newTokenHash, Timestamp.from(newExpiresAt), userAgent, ip
        );
    }

    @Override
    public void revokeFamily(UUID familyId, String reason) {
        // query(...) not update(): see RlsTransactionRunner's set_config note.
        jdbc.query("SELECT rw.rw_fn_revoke_family(?, ?)", rs -> null, familyId, reason);
    }

    @Override
    public boolean revokeByTokenHash(byte[] tokenHash, String reason) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_revoke_by_token_hash(?, ?)", Boolean.class, tokenHash, reason);
        return Boolean.TRUE.equals(result);
    }
}
