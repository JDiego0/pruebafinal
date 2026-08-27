package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelMemberRole;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelMembershipPortJdbc implements ChannelMembershipPort {

    private final JdbcTemplate jdbc;

    public ChannelMembershipPortJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean join(ChannelId channelId) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_join_channel(?)", Boolean.class, channelId.value());
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean add(ChannelId channelId, UserId targetUserId, ChannelMemberRole role) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_add_channel_member(?, ?, ?)", Boolean.class,
                channelId.value(), targetUserId.value(), role.code());
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean leave(ChannelId channelId) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_leave_channel(?)", Boolean.class, channelId.value());
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean remove(ChannelId channelId, UserId targetUserId) {
        Boolean result = jdbc.queryForObject("SELECT rw.rw_fn_remove_channel_member(?, ?)", Boolean.class,
                channelId.value(), targetUserId.value());
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean updateReadCursor(ChannelId channelId, MessageId lastReadMessageId, Boolean muted) {
        Boolean result = jdbc.queryForObject(
                "SELECT rw.rw_fn_update_channel_member(?, NULL, NULL, ?, ?)", Boolean.class,
                channelId.value(), lastReadMessageId == null ? null : lastReadMessageId.value(), muted
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean updateRole(ChannelId channelId, UserId targetUserId, ChannelMemberRole role) {
        Boolean result = jdbc.queryForObject(
                "SELECT rw.rw_fn_update_channel_member(?, ?, ?, NULL, NULL)", Boolean.class,
                channelId.value(), targetUserId.value(), role.code()
        );
        return Boolean.TRUE.equals(result);
    }
}
