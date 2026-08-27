package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.ConversationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class ConversationRepositoryJdbc implements ConversationRepository {

    private final JdbcTemplate jdbc;

    public ConversationRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversationSummary> listMine() {
        return jdbc.query(
                "SELECT rw_channel_id, rw_channel_type, rw_name, rw_slug, rw_topic, rw_is_archived, " +
                        "rw_member_role, rw_last_read_message_id, rw_is_muted, rw_joined_at, " +
                        "rw_last_message_id, rw_last_message_preview, rw_last_message_sender_id, " +
                        "rw_last_message_sender_display_name, rw_last_message_at, rw_last_message_is_deleted, " +
                        "rw_unread_count, rw_counterpart_user_id, rw_counterpart_display_name, rw_counterpart_avatar_url " +
                        "FROM rw.rw_v_user_conversations " +
                        "ORDER BY COALESCE(rw_last_message_at, rw_joined_at) DESC",
                (rs, rowNum) -> {
                    Object lastMessageId = rs.getObject("rw_last_message_id");
                    Object lastReadId = rs.getObject("rw_last_read_message_id");
                    Timestamp lastMessageAt = rs.getTimestamp("rw_last_message_at");
                    Object counterpartId = rs.getObject("rw_counterpart_user_id");
                    return new ConversationSummary(
                            ChannelId.of((UUID) rs.getObject("rw_channel_id")),
                            ChannelType.fromCode(rs.getString("rw_channel_type")),
                            rs.getString("rw_name"),
                            rs.getString("rw_slug"),
                            rs.getString("rw_topic"),
                            rs.getBoolean("rw_is_archived"),
                            ChannelMemberRole.fromCode(rs.getString("rw_member_role")),
                            lastReadId == null ? null : MessageId.of(((Number) lastReadId).longValue()),
                            rs.getBoolean("rw_is_muted"),
                            rs.getTimestamp("rw_joined_at").toInstant(),
                            lastMessageId == null ? null : MessageId.of(((Number) lastMessageId).longValue()),
                            rs.getString("rw_last_message_preview"),
                            rs.getObject("rw_last_message_sender_id") == null ? null : UserId.of((UUID) rs.getObject("rw_last_message_sender_id")),
                            rs.getString("rw_last_message_sender_display_name"),
                            lastMessageAt == null ? null : lastMessageAt.toInstant(),
                            rs.getBoolean("rw_last_message_is_deleted"),
                            rs.getLong("rw_unread_count"),
                            counterpartId == null ? null : UserId.of((UUID) counterpartId),
                            rs.getString("rw_counterpart_display_name"),
                            rs.getString("rw_counterpart_avatar_url")
                    );
                }
        );
    }
}
