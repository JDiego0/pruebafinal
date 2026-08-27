package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.usecase.DeleteMessageUseCase;
import co.riwi.messaging.application.usecase.EditMessageUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.port.MessageRepository.EditResult;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.presentation.dto.EditMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** MSG-01 (logical delete only), MSG-02 (edit preserves history). Sender-only, enforced by RLS. */
@RestController
@RequestMapping("/api/v1/messages")
public class MessagesController {

    private final EditMessageUseCase editMessageUseCase;
    private final DeleteMessageUseCase deleteMessageUseCase;

    public MessagesController(EditMessageUseCase editMessageUseCase, DeleteMessageUseCase deleteMessageUseCase) {
        this.editMessageUseCase = editMessageUseCase;
        this.deleteMessageUseCase = deleteMessageUseCase;
    }

    @PatchMapping("/{messageId}")
    public Map<String, Object> edit(@CurrentActor AuthenticatedActor actor, @PathVariable long messageId, @Valid @RequestBody EditMessageRequest request) {
        EditResult result = editMessageUseCase.execute(actor.userId(), MessageId.of(messageId), request.body());
        return Map.of("messageId", messageId, "revision", result.revision(), "editedAt", result.editedAt());
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> delete(@CurrentActor AuthenticatedActor actor, @PathVariable long messageId) {
        deleteMessageUseCase.execute(actor.userId(), MessageId.of(messageId));
        return ResponseEntity.noContent().build();
    }
}
