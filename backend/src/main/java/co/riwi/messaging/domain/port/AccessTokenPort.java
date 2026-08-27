package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.Role;
import co.riwi.messaging.domain.model.UserId;

/** Issues the short-lived JWT access token (AUTH-02, AUTH-03). */
public interface AccessTokenPort {

    String issue(UserId userId, Role role, DisplayName displayName, Locale locale);

    long ttlSeconds();
}
