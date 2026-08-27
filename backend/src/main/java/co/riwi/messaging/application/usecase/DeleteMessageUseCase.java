package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.MessageRepository;
import co.riwi.messaging.domain.port.TransactionRunner;

/** DELETE /messages/{id} — logical only (MSG-01). Sender-only, enforced by RLS. */
public class DeleteMessageUseCase {

    private final MessageRepository messageRepository;
    private final TransactionRunner transactionRunner;

    public DeleteMessageUseCase(MessageRepository messageRepository, TransactionRunner transactionRunner) {
        this.messageRepository = messageRepository;
        this.transactionRunner = transactionRunner;
    }

    public void execute(UserId actor, MessageId messageId) {
        boolean deleted = transactionRunner.runAsActor(actor, () -> messageRepository.softDelete(messageId));
        if (!deleted) {
            throw new ResourceNotVisible("message", messageId);
        }
    }
}
