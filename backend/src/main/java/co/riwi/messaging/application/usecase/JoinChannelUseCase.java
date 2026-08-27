package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ConflictError;
import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelDetail;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelType;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelMembershipPort;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * POST /channels/{id}/members (self-join). Checks visibility and type
 * FIRST, so an invisible/PRIVATE/non-existent channel is a clean 404
 * (SEC-14) rather than surfacing the RLS INSERT policy's raw violation —
 * which the generic SQLSTATE translator would otherwise render as 403,
 * incorrectly disclosing that the channel exists.
 */
public class JoinChannelUseCase {

    private final ChannelRepository channelRepository;
    private final ChannelMembershipPort membershipPort;
    private final TransactionRunner transactionRunner;

    public JoinChannelUseCase(ChannelRepository channelRepository, ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.membershipPort = membershipPort;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, ChannelId channelId) {
        transactionRunner.runAsActor(actor, () -> {
            ChannelDetail channel = channelRepository.findById(channelId)
                    .orElseThrow(() -> new ResourceNotVisible("channel", channelId));
            if (channel.type() != ChannelType.PUBLIC || channel.archived()) {
                throw new ResourceNotVisible("channel", channelId);
            }
            if (!membershipPort.join(channelId)) {
                throw new ConflictError("already a member of this channel");
            }
            return null;
        });
    }
}
