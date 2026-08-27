package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.ConversationSummary;

import java.time.Instant;

public record ConversationResponse(
        String channelId, String type, String name, String slug, boolean isArchived,
        CounterpartResponse counterpart, LastMessageResponse lastMessage,
        long unreadCount, String memberRole, boolean isMuted
) {
    public record CounterpartResponse(String userId, String displayName, String avatarUrl) {
    }

    public record LastMessageResponse(String messageId, String preview, String senderDisplayName, Instant createdAt, boolean isDeleted) {
    }

    public static ConversationResponse from(ConversationSummary s) {
        CounterpartResponse counterpart = s.counterpartUserId() == null ? null :
                new CounterpartResponse(s.counterpartUserId().toString(), s.counterpartDisplayName(), s.counterpartAvatarUrl());
        LastMessageResponse lastMessage = s.lastMessageId() == null ? null :
                new LastMessageResponse(s.lastMessageId().toString(), s.lastMessagePreview(), s.lastMessageSenderDisplayName(), s.lastMessageAt(), s.lastMessageDeleted());
        return new ConversationResponse(
                s.channelId().toString(), s.type().code(), s.name(), s.slug(), s.archived(),
                counterpart, lastMessage, s.unreadCount(), s.memberRole().code(), s.muted()
        );
    }
}
