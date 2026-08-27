package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.UpdateChannelCommand;
import co.riwi.messaging.domain.error.OperationNotPermitted;
import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelDetail;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * PATCH /channels/{id}. The UPDATE itself is gated by rw_channels' own RLS
 * policy (OWNER/MODERATOR only) — a member who can see the channel but lacks
 * that role gets zero affected rows. D-14: that is exactly the case 403 is
 * reserved for (visible, operation not permitted) — distinct from 404
 * (not visible at all), which the initial SELECT would already have caught.
 */
public class UpdateChannelUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public UpdateChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public ChannelDetail execute(UserId actor, ChannelId channelId, UpdateChannelCommand command) {
        return transactionRunner.runAsActor(actor, () -> {
            ChannelDetail before = channelRepository.findById(channelId)
                    .orElseThrow(() -> new ResourceNotVisible("channel", channelId));

            boolean updated = channelRepository.update(channelId, command.name(), command.topic(), command.archived());
            if (!updated) {
                throw new OperationNotPermitted("only an OWNER or MODERATOR may update this channel");
            }
            return channelRepository.findById(channelId).orElse(before);
        });
    }
}
