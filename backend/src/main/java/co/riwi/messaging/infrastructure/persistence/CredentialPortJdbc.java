package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.CredentialLookup;
import co.riwi.messaging.domain.model.Email;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.CredentialPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls the two SECURITY DEFINER auth functions from V4 — the only path to
 * rw_user_credentials, which rw_app has zero table grant on (D-16). Both run
 * via {@code TransactionRunner#runAnonymous}: there is no actor yet at login.
 */
@Repository
public class CredentialPortJdbc implements CredentialPort {

    private final JdbcTemplate jdbc;

    public CredentialPortJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CredentialLookup> findForLogin(Email email) {
        return jdbc.query(
                "SELECT rw_user_id, rw_password_hash, rw_failed_attempts, rw_locked_until, rw_is_active " +
                        "FROM rw.rw_fn_get_credential_for_login(?)",
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    Timestamp lockedUntil = rs.getTimestamp("rw_locked_until");
                    return Optional.of(new CredentialLookup(
                            UserId.of((UUID) rs.getObject("rw_user_id")),
                            rs.getString("rw_password_hash"),
                            rs.getInt("rw_failed_attempts"),
                            lockedUntil == null ? null : lockedUntil.toInstant(),
                            rs.getBoolean("rw_is_active")
                    ));
                },
                email.value()
        );
    }

    @Override
    public void recordLoginAttempt(UserId userId, boolean success) {
        // query(...) not update(): see RlsTransactionRunner's set_config note
        // — calling a void-returning SQL function via SELECT still yields a
        // result set, which executeUpdate() (what .update() uses) rejects.
        jdbc.query("SELECT rw.rw_fn_record_login_attempt(?, ?)", rs -> null, userId.value(), success);
    }
}
