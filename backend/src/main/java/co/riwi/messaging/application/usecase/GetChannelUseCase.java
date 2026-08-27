package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelDetail;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/** GET /channels/{id} — 404 whether missing or merely unauthorized (SEC-14). */
public class GetChannelUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public GetChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public ChannelDetail execute(UserId actor, ChannelId channelId) {
        return transactionRunner.runAsActor(actor, () -> channelRepository.findById(channelId))
                .orElseThrow(() -> new ResourceNotVisible("channel", channelId));
    }
}
