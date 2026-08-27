package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.usecase.AskCopilotUseCase;
import co.riwi.messaging.application.usecase.GetCopilotUsageUseCase;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import co.riwi.messaging.presentation.dto.AskCopilotRequest;
import co.riwi.messaging.presentation.dto.CopilotAnswerResponse;
import co.riwi.messaging.presentation.dto.CopilotUsageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * AI-01..AI-15, OBJ-07.
 * POST /api/v1/copilot/ask     — run the RAG pipeline
 * GET  /api/v1/copilot/usage   — usage ledger for the authenticated user
 *
 * Actor ID always comes from JWT (@CurrentActor), never from the request body (SEC-01).
 */
@RestController
@RequestMapping("/api/v1/copilot")
public class CopilotController {

    private final AskCopilotUseCase askCopilotUseCase;
    private final GetCopilotUsageUseCase getCopilotUsageUseCase;

    public CopilotController(AskCopilotUseCase askCopilotUseCase,
                              GetCopilotUsageUseCase getCopilotUsageUseCase) {
        this.askCopilotUseCase = askCopilotUseCase;
        this.getCopilotUsageUseCase = getCopilotUsageUseCase;
    }

    @PostMapping("/ask")
    public ResponseEntity<CopilotAnswerResponse> ask(
            @CurrentActor AuthenticatedActor actor,
            @Valid @RequestBody AskCopilotRequest request
    ) {
        ChannelId channelId = request.channelId() != null
                ? new ChannelId(UUID.fromString(request.channelId()))
                : null;
        UUID threadId = request.threadId() != null
                ? UUID.fromString(request.threadId())
                : null;

        AskCopilotUseCase.AskResult result = askCopilotUseCase.execute(
                new AskCopilotUseCase.AskCommand(actor.userId(), request.question(), channelId, threadId)
        );

        return ResponseEntity.ok(CopilotAnswerResponse.from(result));
    }

    @GetMapping("/usage")
    public ResponseEntity<CopilotUsageResponse> usage(@CurrentActor AuthenticatedActor actor) {
        try {
            var summary = getCopilotUsageUseCase.execute(actor.userId());
            return ResponseEntity.ok(CopilotUsageResponse.from(summary));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // User has no copilot history yet
            return ResponseEntity.ok(new CopilotUsageResponse(
                    actor.userId().value().toString(), 0, 0, 0, 0, null, null
            ));
        }
    }
}
