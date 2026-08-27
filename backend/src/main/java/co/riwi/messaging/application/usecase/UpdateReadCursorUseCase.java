package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import co.riwi.messaging.domain.port.TransactionRunner;

/** PUT /channels/{id}/read-cursor — self only. */
public class UpdateReadCursorUseCase {

    private final ChannelMembershipPort membershipPort;
    private final TransactionRunner transactionRunner;

    public UpdateReadCursorUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        this.membershipPort = membershipPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, ChannelId channelId, MessageId lastReadMessageId) {
        transactionRunner.runAsActor(actor, () -> membershipPort.updateReadCursor(channelId, lastReadMessageId, null));
    }
}
