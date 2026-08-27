package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * DELETE /channels/{id}/members/{self}. Not currently a member -> 404 (the
 * membership resource being deleted does not exist), matching standard REST
 * DELETE semantics. BR-10 (last owner) and BR-07 (DIRECT immutability) are
 * enforced by database triggers regardless of this use case; a raised
 * violation surfaces as 409 via the SQLSTATE translator (23514 -> conflict).
 */
public class LeaveChannelUseCase {

    private final ChannelMembershipPort membershipPort;
    private final TransactionRunner transactionRunner;

    public LeaveChannelUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        this.membershipPort = membershipPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, ChannelId channelId) {
        boolean left = transactionRunner.runAsActor(actor, () -> membershipPort.leave(channelId));
        if (!left) {
            throw new ResourceNotVisible("channel membership", channelId);
        }
    }
}
