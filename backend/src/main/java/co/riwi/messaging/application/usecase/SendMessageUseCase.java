package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.SendMessageCommand;
import co.riwi.messaging.domain.error.OperationNotPermitted;
import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.MessageRepository;
import co.riwi.messaging.domain.port.MessageRepository.SendResult;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * POST /channels/{id}/messages. Not a member -> 404 (never disclose that a
 * private channel exists); a member posting to an archived channel -> 403
 * (docs/API.md §5) — the RLS INSERT policy would reject both cases with the
 * same SQLSTATE, so the distinction is made here, before the INSERT is
 * even attempted, exactly like GetMessageHistoryUseCase.
 */
public class SendMessageUseCase {

    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final TransactionRunner transactionRunner;

    public SendMessageUseCase(ChannelRepository channelRepository, MessageRepository messageRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.transactionRunner = transactionRunner;
    }

    public SendResult execute(UserId actor, ChannelId channelId, SendMessageCommand command) {
        MessageBody body = MessageBody.of(command.body());

        return transactionRunner.runAsActor(actor, () -> {
            if (!channelRepository.isMember(channelId)) {
                throw new ResourceNotVisible("channel", channelId);
            }
            ChannelDetail channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new ResourceNotVisible("channel", channelId));
            if (channel.archived()) {
                throw new OperationNotPermitted("cannot post to an archived channel");
            }
            MessageId parentId = command.parentMessageId() == null ? null : MessageId.of(command.parentMessageId());
            return messageRepository.send(channelId, body.value(), command.clientMessageId(), parentId);
        });
    }
}
