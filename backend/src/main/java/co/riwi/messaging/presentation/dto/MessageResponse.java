package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.MessageRecord;

import java.time.Instant;

public record MessageResponse(
        long messageId, String channelId, String senderId, Long parentMessageId,
        String body, String clientMessageId, boolean isDeleted, int revision,
        Instant createdAt, Instant editedAt
) {
    public static MessageResponse from(MessageRecord m) {
        return new MessageResponse(
                m.messageId().value(), m.channelId().toString(), m.senderId().toString(),
                m.parentMessageId() == null ? null : m.parentMessageId().value(),
                m.body(), m.clientMessageId().toString(), m.deleted(), m.revision(),
                m.createdAt(), m.editedAt()
        );
    }
}
