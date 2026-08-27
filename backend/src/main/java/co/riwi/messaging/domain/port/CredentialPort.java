package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.CredentialLookup;
import co.riwi.messaging.domain.model.Email;
import co.riwi.messaging.domain.model.UserId;

import java.util.Optional;

/**
 * The only path to rw_user_credentials, which rw_app has zero table grant on
 * (D-16) — every method here calls a SECURITY DEFINER function instead.
 * Argon2id verification itself happens in infrastructure, never in SQL
 * (PostgreSQL has no Argon2 support).
 */
public interface CredentialPort {

    Optional<CredentialLookup> findForLogin(Email email);

    void recordLoginAttempt(UserId userId, boolean success);
}
