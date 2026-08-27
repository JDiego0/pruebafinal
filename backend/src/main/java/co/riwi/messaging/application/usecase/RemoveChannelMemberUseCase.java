package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * DELETE /channels/{id}/members/{userId} (owner/moderator removing someone
 * else). rw_fn_remove_channel_member checks OWNER/MODERATOR internally and
 * raises 42501 (-> 403) if the actor lacks it; a false return here means the
 * target was already not a member.
 */
public class RemoveChannelMemberUseCase {

    private final ChannelMembershipPort membershipPort;
    private final TransactionRunner transactionRunner;

    public RemoveChannelMemberUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        this.membershipPort = membershipPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, ChannelId channelId, UserId targetUserId) {
        boolean removed = transactionRunner.runAsActor(actor, () -> membershipPort.remove(channelId, targetUserId));
        if (!removed) {
            throw new ResourceNotVisible("channel membership", targetUserId);
        }
    }
}
