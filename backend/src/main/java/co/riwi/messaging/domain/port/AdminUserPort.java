package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.Role;
import co.riwi.messaging.domain.model.UserId;

/**
 * Reaches rw_sp_modify_user (OBJ-02), which internally re-checks that the
 * calling actor is either editing themselves (profile fields only) or is an
 * ADMIN (role change / deactivation). This port never accepts an "acting as
 * admin" flag from the caller — the procedure decides, from the actor
 * already set on the transaction, not from anything passed in.
 */
public interface AdminUserPort {

    void modify(UserId targetUserId, DisplayName displayName, Locale locale, String avatarUrl, Role role, Boolean deactivate);
}
