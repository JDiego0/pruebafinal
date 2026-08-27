package co.riwi.messaging.domain.model;

import java.time.Instant;

/**
 * One row of rw_v_user_conversations — the authenticated actor's own
 * conversations only (OBJ-03). Never constructed for a channel the actor
 * merely could discover but has not joined.
 */
public record ConversationSummary(
        ChannelId channelId,
        ChannelType type,
        String name,
        String slug,
        String topic,
        boolean archived,
        ChannelMemberRole memberRole,
        MessageId lastReadMessageId,
        boolean muted,
        Instant joinedAt,
        MessageId lastMessageId,
        String lastMessagePreview,
        UserId lastMessageSenderId,
        String lastMessageSenderDisplayName,
        Instant lastMessageAt,
        boolean lastMessageDeleted,
        long unreadCount,
        UserId counterpartUserId,
        String counterpartDisplayName,
        String counterpartAvatarUrl
) {
}
