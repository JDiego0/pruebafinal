package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.ChannelRepository.DirectChannelResult;
import co.riwi.messaging.domain.port.TransactionRunner;

/** POST /channels/direct — idempotent per BR-08 (rw_fn_open_direct_channel). */
public class OpenDirectChannelUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public OpenDirectChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public DirectChannelResult execute(UserId actor, UserId counterpart) {
        return transactionRunner.runAsActor(actor, () -> channelRepository.openDirect(counterpart));
    }
}
