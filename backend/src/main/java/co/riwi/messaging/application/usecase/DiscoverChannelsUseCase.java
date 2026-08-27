package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ChannelDetail;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.util.List;

/** GET /channels — discoverable PUBLIC channels only (IBR-02). */
public class DiscoverChannelsUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public DiscoverChannelsUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<ChannelDetail> execute(UserId actor, String search) {
        return transactionRunner.runAsActor(actor, () -> channelRepository.discoverPublic(search));
    }
}
