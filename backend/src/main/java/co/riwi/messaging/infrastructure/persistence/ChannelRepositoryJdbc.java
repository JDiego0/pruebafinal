package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.ChannelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChannelRepositoryJdbc implements ChannelRepository {

    private final JdbcTemplate jdbc;

    public ChannelRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ChannelDetail> CHANNEL_MAPPER = (ResultSet rs, int rowNum) -> new ChannelDetail(
            ChannelId.of((UUID) rs.getObject("rw_channel_id")),
            ChannelType.fromCode(rs.getString("rw_channel_type_code")),
            rs.getString("rw_name"),
            rs.getString("rw_slug"),
            rs.getString("rw_topic"),
            rs.getBoolean("rw_is_archived"),
            UserId.of((UUID) rs.getObject("rw_created_by")),
            rs.getTimestamp("rw_created_at").toInstant()
    );

    private static final String SELECT_BASE =
            "SELECT c.rw_channel_id, ct.rw_channel_type_code, c.rw_name, c.rw_slug, c.rw_topic, " +
                    "c.rw_is_archived, c.rw_created_by, c.rw_created_at " +
                    "FROM rw.rw_channels c JOIN rw.rw_channel_types ct ON ct.rw_channel_type_id = c.rw_channel_type_id ";

    @Override
    public Optional<ChannelDetail> findById(ChannelId id) {
        return jdbc.query(SELECT_BASE + "WHERE c.rw_channel_id = ?",
                rs -> rs.next() ? Optional.of(CHANNEL_MAPPER.mapRow(rs, 1)) : Optional.empty(),
                id.value());
    }

    @Override
    public boolean isMember(ChannelId id) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_is_channel_member(?)", Boolean.class, id.value());
        return Boolean.TRUE.equals(result);
    }

    @Override
    public List<ChannelDetail> discoverPublic(String search) {
        // RLS already restricts this SELECT to PUBLIC, non-archived channels
        // for any authenticated actor (docs/SECURITY_MODEL.md §4) — the
        // WHERE clause here narrows further, it does not widen.
        return jdbc.query(SELECT_BASE +
                        "WHERE ct.rw_channel_type_code = 'PUBLIC' AND NOT c.rw_is_archived " +
                        // The casts are required: PostgreSQL cannot infer a type
                        // for a bare parameter compared only against NULL.
                        "AND (?::text IS NULL OR ?::text = '' OR c.rw_name ILIKE '%' || ?::text || '%') " +
                        "ORDER BY c.rw_name",
                CHANNEL_MAPPER, search, search, search);
    }

    @Override
    public ChannelId create(ChannelType type, String name, ChannelSlug slug, String topic) {
        UUID id = jdbc.queryForObject(
                "SELECT rw.rw_fn_create_channel(?, ?, ?, ?)", UUID.class,
                type.code(), name, slug == null ? null : slug.value(), topic
        );
        return ChannelId.of(id);
    }

    @Override
    public DirectChannelResult openDirect(UserId counterpart) {
        return jdbc.queryForObject(
                "SELECT rw_channel_id, rw_was_created FROM rw.rw_fn_open_direct_channel(?)",
                (rs, rowNum) -> new DirectChannelResult(ChannelId.of((UUID) rs.getObject("rw_channel_id")), rs.getBoolean("rw_was_created")),
                counterpart.value()
        );
    }

    @Override
    public boolean update(ChannelId id, String name, String topic, Boolean archived) {
        int rows = jdbc.update(
                "UPDATE rw.rw_channels SET " +
                        "rw_name = COALESCE(?, rw_name), rw_topic = COALESCE(?, rw_topic), " +
                        "rw_is_archived = COALESCE(?, rw_is_archived) " +
                        "WHERE rw_channel_id = ?",
                name, topic, archived, id.value()
        );
        return rows > 0;
    }

    @Override
    public List<ChannelMemberSummary> listMembers(ChannelId id) {
        return jdbc.query(
                "SELECT m.rw_user_id, u.rw_display_name, u.rw_avatar_url, r.rw_channel_member_role_code, m.rw_joined_at " +
                        "FROM rw.rw_channel_members m " +
                        "JOIN rw.rw_users u ON u.rw_user_id = m.rw_user_id " +
                        "JOIN rw.rw_channel_member_roles r ON r.rw_channel_member_role_id = m.rw_channel_member_role_id " +
                        "WHERE m.rw_channel_id = ? ORDER BY u.rw_display_name",
                (rs, rowNum) -> new ChannelMemberSummary(
                        UserId.of((UUID) rs.getObject("rw_user_id")),
                        rs.getString("rw_display_name"),
                        rs.getString("rw_avatar_url"),
                        ChannelMemberRole.fromCode(rs.getString("rw_channel_member_role_code")),
                        rs.getTimestamp("rw_joined_at").toInstant()
                ),
                id.value()
        );
    }
}
