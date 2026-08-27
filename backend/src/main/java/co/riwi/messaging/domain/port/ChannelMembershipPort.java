package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelMemberRole;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;

/**
 * Every write here beyond self-join goes through a SECURITY DEFINER function
 * (D-08) because rw_app holds no DELETE grant on rw_channel_members and no
 * direct UPDATE grant either — see rw_fn_update_channel_member's column-set
 * separation note in V4.
 */
public interface ChannelMembershipPort {

    boolean join(ChannelId channelId);

    /** Owner/moderator adds someone else — self-join uses {@link #join} instead. */
    boolean add(ChannelId channelId, UserId targetUserId, ChannelMemberRole role);

    boolean leave(ChannelId channelId);

    boolean remove(ChannelId channelId, UserId targetUserId);

    boolean updateReadCursor(ChannelId channelId, MessageId lastReadMessageId, Boolean muted);

    boolean updateRole(ChannelId channelId, UserId targetUserId, ChannelMemberRole role);
}
