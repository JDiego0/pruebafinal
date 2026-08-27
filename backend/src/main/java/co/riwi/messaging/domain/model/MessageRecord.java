package co.riwi.messaging.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A row of channel history. {@code body} is null exactly when
 * {@code deleted} is true (BR-06) — the tombstone shape, not an omission.
 */
public record MessageRecord(
        MessageId messageId,
        ChannelId channelId,
        UserId senderId,
        String senderDisplayName,
        MessageId parentMessageId,
        String body,
        UUID clientMessageId,
        boolean deleted,
        int revision,
        Instant createdAt,
        Instant editedAt
) {
}
