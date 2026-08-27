package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.SearchHit;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.MessageSearchRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.util.List;

/** GET /search/messages — SRCH-01..SRCH-04. */
public class SearchMessagesUseCase {

    private final MessageSearchRepository searchRepository;
    private final TransactionRunner transactionRunner;

    public SearchMessagesUseCase(MessageSearchRepository searchRepository, TransactionRunner transactionRunner) {
        this.searchRepository = searchRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<SearchHit> execute(UserId actor, String query, ChannelId channelId, Float beforeRank, MessageId beforeMessageId, int limit) {
        return transactionRunner.runAsActor(actor, () -> searchRepository.search(query, channelId, beforeRank, beforeMessageId, limit));
    }
}
