package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.DisplayName;
import co.riwi.messaging.domain.model.Locale;
import co.riwi.messaging.domain.model.Role;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.AdminUserPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Calls rw_sp_modify_user (OBJ-02) — see AdminUserPort for the authorization note. */
@Repository
public class AdminUserPortJdbc implements AdminUserPort {

    private final JdbcTemplate jdbc;

    public AdminUserPortJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void modify(UserId targetUserId, DisplayName displayName, Locale locale, String avatarUrl, Role role, Boolean deactivate) {
        jdbc.update(
                "CALL rw.rw_sp_modify_user(?, ?, ?, ?, ?, ?)",
                targetUserId.value(),
                displayName == null ? null : displayName.value(),
                locale == null ? null : locale.code(),
                avatarUrl,
                role == null ? null : role.code(),
                deactivate
        );
    }
}
