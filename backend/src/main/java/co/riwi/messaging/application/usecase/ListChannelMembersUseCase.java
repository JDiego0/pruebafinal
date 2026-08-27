package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.ChannelMemberSummary;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ChannelRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.util.List;

/** GET /channels/{id}/members — members only, SEC-09. */
public class ListChannelMembersUseCase {

    private final ChannelRepository channelRepository;
    private final TransactionRunner transactionRunner;

    public ListChannelMembersUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        this.channelRepository = channelRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<ChannelMemberSummary> execute(UserId actor, ChannelId channelId) {
        return transactionRunner.runAsActor(actor, () -> channelRepository.listMembers(channelId));
    }
}
