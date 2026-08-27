package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.usecase.ListConversationsUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.presentation.dto.ConversationResponse;
import co.riwi.messaging.presentation.dto.PageInfo;
import co.riwi.messaging.presentation.dto.PagedResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OBJ-03: the authenticated user's conversations. */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationsController {

    private final ListConversationsUseCase listConversationsUseCase;

    public ConversationsController(ListConversationsUseCase listConversationsUseCase) {
        this.listConversationsUseCase = listConversationsUseCase;
    }

    @GetMapping
    public PagedResponse<ConversationResponse> list(@CurrentActor AuthenticatedActor actor) {
        var items = listConversationsUseCase.execute(actor.userId()).stream().map(ConversationResponse::from).toList();
        return new PagedResponse<>(items, new PageInfo(null, false));
    }
}
