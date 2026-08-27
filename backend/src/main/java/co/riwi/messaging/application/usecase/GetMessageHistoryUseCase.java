package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.MessageRecord;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.MessageRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.time.Instant;
import java.util.List;

/**
 * GET /channels/{id}/messages — OBJ-04, keyset only (MSG-04). Checks channel
 * visibility first (SEC-14): a non-member gets 404, distinct from a member
 * viewing a genuinely empty channel, which returns 200 with an empty list.
 */
public class GetMessageHistoryUseCase {

    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final TransactionRunner transactionRunner;

    public GetMessageHistoryUseCase(ChannelRepository channelRepository, MessageRepository messageRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<MessageRecord> execute(UserId actor, ChannelId channelId, Instant beforeCreatedAt, MessageId beforeMessageId, int limit) {
        return transactionRunner.runAsActor(actor, () -> {
            // isMember, not merely findById: a discoverable-but-unjoined
            // PUBLIC channel is visible via findById but its message history
            // must still 404 for a non-member (docs/API.md §5).
            if (!channelRepository.isMember(channelId)) {
                throw new ResourceNotVisible("channel", channelId);
            }
            return messageRepository.history(channelId, beforeCreatedAt, beforeMessageId, limit);
        });
    }
}
