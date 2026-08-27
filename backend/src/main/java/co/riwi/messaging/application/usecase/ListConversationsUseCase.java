package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.ConversationSummary;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.ConversationRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

import java.util.List;

/** GET /conversations (OBJ-03). */
public class ListConversationsUseCase {

    private final ConversationRepository conversationRepository;
    private final TransactionRunner transactionRunner;

    public ListConversationsUseCase(ConversationRepository conversationRepository, TransactionRunner transactionRunner) {
        this.conversationRepository = conversationRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<ConversationSummary> execute(UserId actor) {
        return transactionRunner.runAsActor(actor, conversationRepository::listMine);
    }
}
