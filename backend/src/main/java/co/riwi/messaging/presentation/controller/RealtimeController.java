package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.usecase.IssueRealtimeTicketUseCase;
import co.riwi.messaging.domain.error.AuthenticationFailed;
import co.riwi.messaging.domain.model.AuthenticatedActor;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.RealtimeTicketPort;
import co.riwi.messaging.infrastructure.realtime.SseHub;
import co.riwi.messaging.infrastructure.web.CurrentActor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * D-15: {@code EventSource} cannot set an Authorization header, so the
 * stream is gated by a single-use ticket obtained with a normal bearer
 * token first, rather than putting a long-lived credential in the URL.
 */
@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private static final long TICKET_TTL_SECONDS = 60;

    private final IssueRealtimeTicketUseCase issueRealtimeTicketUseCase;
    private final RealtimeTicketPort ticketPort;
    private final SseHub sseHub;

    public RealtimeController(IssueRealtimeTicketUseCase issueRealtimeTicketUseCase, RealtimeTicketPort ticketPort, SseHub sseHub) {
        this.issueRealtimeTicketUseCase = issueRealtimeTicketUseCase;
        this.ticketPort = ticketPort;
        this.sseHub = sseHub;
    }

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, Object>> issueTicket(@CurrentActor AuthenticatedActor actor) {
        String ticket = issueRealtimeTicketUseCase.execute(actor.userId());
        return ResponseEntity.status(201).body(Map.of("ticket", ticket, "expiresIn", TICKET_TTL_SECONDS));
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam String ticket) {
        UserId userId = ticketPort.consume(ticket)
                .orElseThrow(() -> new AuthenticationFailed("Invalid or expired realtime ticket."));
        return sseHub.register(userId);
    }
}
