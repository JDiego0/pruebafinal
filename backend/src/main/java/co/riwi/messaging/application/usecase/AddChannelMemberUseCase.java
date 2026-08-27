package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ConflictError;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelMemberRole;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * POST /channels/{id}/members with a target userId — owner/moderator invites
 * someone else. rw_fn_add_channel_member checks OWNER/MODERATOR internally
 * and raises 42501 (-> 403) if the actor lacks it.
 */
public class AddChannelMemberUseCase {

    private final ChannelMembershipPort membershipPort;
    private final TransactionRunner transactionRunner;

    public AddChannelMemberUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        this.membershipPort = membershipPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, ChannelId channelId, UserId targetUserId, String roleCode) {
        ChannelMemberRole role = roleCode == null ? ChannelMemberRole.MEMBER : ChannelMemberRole.fromCode(roleCode);
        boolean added = transactionRunner.runAsActor(actor, () -> membershipPort.add(channelId, targetUserId, role));
        if (!added) {
            throw new ConflictError("already a member of this channel");
        }
    }
}
