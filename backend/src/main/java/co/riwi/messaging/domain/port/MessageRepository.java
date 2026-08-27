package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.MessageRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Every method here relies on rw_messages' own RLS policy for authorization
 * (SEC-01, SEC-03) — a non-member gets zero rows regardless of channelId,
 * never an exception that would disclose whether the channel exists.
 */
public interface MessageRepository {

    /** OBJ-04: keyset pagination, no OFFSET (MSG-04). */
    List<MessageRecord> history(ChannelId channelId, Instant beforeCreatedAt, MessageId beforeMessageId, int limit);

    /** IBR-04: idempotent by (channel, sender, clientMessageId). */
    SendResult send(ChannelId channelId, String body, UUID clientMessageId, MessageId parentMessageId);

    EditResult edit(MessageId messageId, String newBody);

    /** MSG-01: logical delete only. */
    boolean softDelete(MessageId messageId);

    record SendResult(MessageId messageId, Instant createdAt, boolean created) {
    }

    record EditResult(int revision, Instant editedAt) {
    }
}
