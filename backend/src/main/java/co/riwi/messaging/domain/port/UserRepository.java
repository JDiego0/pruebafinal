package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.model.UserProfile;

import java.util.List;
import java.util.Optional;

/**
 * Directory reads and self-service profile updates. Every method here runs
 * inside {@link TransactionRunner#runAsActor} and is filtered by
 * rw_users' own RLS policy (self-row UPDATE, active-only SELECT) — nothing
 * here needs an internal authorization check because the database already
 * provides it.
 */
public interface UserRepository {

    Optional<UserProfile> findById(UserId id);

    UserProfile updateSelfProfile(UserId id, DisplayName displayName, Locale locale, String avatarUrl);

    List<UserQueryRow> query(String search, int limit, String cursorDisplayName, UserId cursorUserId);

    record UserQueryRow(UserId userId, DisplayName displayName, String email, String avatarUrl, String roleCode, Locale locale) {
    }
}
