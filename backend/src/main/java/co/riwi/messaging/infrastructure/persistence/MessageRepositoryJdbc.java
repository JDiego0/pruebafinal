package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.MessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MessageRepositoryJdbc implements MessageRepository {

    private final JdbcTemplate jdbc;

    public MessageRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MessageRecord> history(ChannelId channelId, Instant beforeCreatedAt, MessageId beforeMessageId, int limit) {
        return jdbc.query(
                // The join is on rw_users, which RLS exposes to any authenticated
                // actor as directory data — it adds a display name, never a
                // message the caller could not already read.
                "SELECT h.rw_message_id, h.rw_channel_id, h.rw_sender_id, u.rw_display_name, " +
                        "h.rw_parent_message_id, h.rw_body, h.rw_client_message_id, h.rw_is_deleted, " +
                        "h.rw_revision, h.rw_created_at, h.rw_edited_at " +
                        "FROM rw.rw_fn_message_history(?, ?, ?, ?) h " +
                        "LEFT JOIN rw.rw_users u ON u.rw_user_id = h.rw_sender_id",
                (rs, rowNum) -> {
                    Timestamp editedAt = rs.getTimestamp("rw_edited_at");
                    Object parent = rs.getObject("rw_parent_message_id");
                    return new MessageRecord(
                            MessageId.of(rs.getLong("rw_message_id")),
                            ChannelId.of((UUID) rs.getObject("rw_channel_id")),
                            UserId.of((UUID) rs.getObject("rw_sender_id")),
                            rs.getString("rw_display_name"),
                            parent == null ? null : MessageId.of(((Number) parent).longValue()),
                            rs.getString("rw_body"),
                            (UUID) rs.getObject("rw_client_message_id"),
                            rs.getBoolean("rw_is_deleted"),
                            rs.getInt("rw_revision"),
                            rs.getTimestamp("rw_created_at").toInstant(),
                            editedAt == null ? null : editedAt.toInstant()
                    );
                },
                channelId.value(), beforeCreatedAt == null ? null : Timestamp.from(beforeCreatedAt),
                beforeMessageId == null ? null : beforeMessageId.value(), limit
        );
    }

    @Override
    public SendResult send(ChannelId channelId, String body, UUID clientMessageId, MessageId parentMessageId) {
        return jdbc.queryForObject(
                "SELECT rw_message_id, rw_created_at, rw_was_created FROM rw.rw_fn_send_message(?, ?, ?, ?)",
                (rs, rowNum) -> new SendResult(
                        MessageId.of(rs.getLong("rw_message_id")),
                        rs.getTimestamp("rw_created_at").toInstant(),
                        rs.getBoolean("rw_was_created")
                ),
                channelId.value(), body, clientMessageId, parentMessageId == null ? null : parentMessageId.value()
        );
    }

    @Override
    public EditResult edit(MessageId messageId, String newBody) {
        return jdbc.queryForObject(
                "SELECT rw_revision, rw_edited_at FROM rw.rw_fn_edit_message(?, ?)",
                (rs, rowNum) -> new EditResult(rs.getInt("rw_revision"), rs.getTimestamp("rw_edited_at").toInstant()),
                messageId.value(), newBody
        );
    }

    @Override
    public boolean softDelete(MessageId messageId) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_soft_delete_message(?)", Boolean.class, messageId.value());
        return Boolean.TRUE.equals(result);
    }
}
