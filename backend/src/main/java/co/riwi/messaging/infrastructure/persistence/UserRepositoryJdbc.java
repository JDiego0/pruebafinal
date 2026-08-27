package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * rw_users reads and self-service updates run through plain RLS-governed
 * SQL (SECURITY INVOKER throughout) — the SELECT/UPDATE policies already do
 * the authorization work, so this adapter adds nothing beyond mapping.
 * {@link #query} calls the OBJ-01 stored procedure, which returns its result
 * set through a REFCURSOR OUT parameter (PostgreSQL procedures cannot return
 * a row set any other way) — this requires a real transaction, which is
 * guaranteed by RlsTransactionRunner.
 */
@Repository
public class UserRepositoryJdbc implements UserRepository {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public UserRepositoryJdbc(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public Optional<UserProfile> findById(UserId id) {
        return jdbc.query(
                "SELECT u.rw_user_id, u.rw_email, u.rw_display_name, r.rw_role_code, u.rw_locale, " +
                        "u.rw_avatar_url, u.rw_is_active, u.rw_created_at, u.rw_last_seen_at " +
                        "FROM rw.rw_users u JOIN rw.rw_roles r ON r.rw_role_id = u.rw_role_id " +
                        "WHERE u.rw_user_id = ?",
                rs -> rs.next() ? Optional.of(mapProfile(rs)) : Optional.empty(),
                id.value()
        );
    }

    @Override
    public UserProfile updateSelfProfile(UserId id, DisplayName displayName, Locale locale, String avatarUrl) {
        // Partial update: PATCH /me accepts any subset of fields (docs/API.md
        // §3), so an omitted (null) field must leave the column unchanged.
        jdbc.update(
                "UPDATE rw.rw_users SET " +
                        "rw_display_name = COALESCE(?, rw_display_name), " +
                        "rw_locale = COALESCE(?, rw_locale), " +
                        "rw_avatar_url = COALESCE(?, rw_avatar_url) " +
                        "WHERE rw_user_id = ?",
                displayName == null ? null : displayName.value(),
                locale == null ? null : locale.code(),
                avatarUrl, id.value()
        );
        return findById(id).orElseThrow(() -> new IllegalStateException("self-update affected no visible row"));
    }

    @Override
    public List<UserQueryRow> query(String search, int limit, String cursorDisplayName, UserId cursorUserId) {
        // OBJ-01 (rw_sp_query_users): a PostgreSQL PROCEDURE returns a result
        // set only via a REFCURSOR OUT parameter — CALL it, then FETCH from
        // the cursor it opens, both inside the one transaction the caller is
        // already running in (RlsTransactionRunner guarantees this).
        // Literal "CALL ...", not the {call ...} JDBC escape: PGJDBC
        // translates the escape form to a function-style invocation for a
        // statement with a registered OUT parameter, which PostgreSQL
        // rejects for a genuine PROCEDURE ("... is a procedure, use CALL").
        // Found by exercising this endpoint against a live database.
        Connection con = DataSourceUtils.getConnection(dataSource);
        try (CallableStatement cs = con.prepareCall("CALL rw.rw_sp_query_users(?, ?, ?, ?, ?)")) {
            cs.setString(1, search);
            cs.setInt(2, limit);
            cs.setString(3, cursorDisplayName);
            if (cursorUserId != null) {
                cs.setObject(4, cursorUserId.value(), Types.OTHER);
            } else {
                cs.setNull(4, Types.OTHER);
            }
            cs.registerOutParameter(5, Types.REF_CURSOR);
            cs.execute();

            List<UserQueryRow> rows = new ArrayList<>();
            try (ResultSet rs = (ResultSet) cs.getObject(5)) {
                while (rs.next()) {
                    rows.add(new UserQueryRow(
                            UserId.of((UUID) rs.getObject("rw_user_id")),
                            DisplayName.of(rs.getString("rw_display_name")),
                            rs.getString("rw_email"),
                            rs.getString("rw_avatar_url"),
                            rs.getString("rw_role_code"),
                            Locale.fromCode(rs.getString("rw_locale"))
                    ));
                }
            }
            return rows;
        } catch (java.sql.SQLException e) {
            throw new org.springframework.dao.DataAccessResourceFailureException("rw_sp_query_users failed", e);
        } finally {
            DataSourceUtils.releaseConnection(con, dataSource);
        }
    }

    private UserProfile mapProfile(ResultSet rs) throws java.sql.SQLException {
        Timestamp lastSeen = rs.getTimestamp("rw_last_seen_at");
        return new UserProfile(
                UserId.of((UUID) rs.getObject("rw_user_id")),
                Email.of(rs.getString("rw_email")),
                DisplayName.of(rs.getString("rw_display_name")),
                Role.fromCode(rs.getString("rw_role_code")),
                Locale.fromCode(rs.getString("rw_locale")),
                rs.getString("rw_avatar_url"),
                rs.getBoolean("rw_is_active"),
                rs.getTimestamp("rw_created_at").toInstant(),
                lastSeen == null ? null : lastSeen.toInstant()
        );
    }
}
