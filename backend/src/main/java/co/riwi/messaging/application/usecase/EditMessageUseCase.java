package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.MessageBody;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.MessageRepository;
import co.riwi.messaging.domain.port.MessageRepository.EditResult;
import co.riwi.messaging.domain.port.TransactionRunner;

/**
 * PATCH /messages/{id} — sender only, not deleted (MSG-02). A mismatch
 * raises P0002 inside rw_fn_edit_message, translated to 404 by
 * GlobalExceptionHandler — SEC-14 applies here too: "not yours" and
 * "does not exist" are indistinguishable.
 */
public class EditMessageUseCase {

    private final MessageRepository messageRepository;
    private final TransactionRunner transactionRunner;

    public EditMessageUseCase(MessageRepository messageRepository, TransactionRunner transactionRunner) {
        this.messageRepository = messageRepository;
        this.transactionRunner = transactionRunner;
    }

    public EditResult execute(UserId actor, MessageId messageId, String newBody) {
        MessageBody body = MessageBody.of(newBody);
        return transactionRunner.runAsActor(actor, () -> messageRepository.edit(messageId, body.value()));
    }
}
