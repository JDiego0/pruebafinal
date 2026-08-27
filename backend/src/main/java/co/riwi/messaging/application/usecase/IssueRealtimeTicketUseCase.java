package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.RealtimeTicketPort;

/** POST /realtime/ticket (D-15). */
public class IssueRealtimeTicketUseCase {

    private final RealtimeTicketPort ticketPort;

    public IssueRealtimeTicketUseCase(RealtimeTicketPort ticketPort) {
        this.ticketPort = ticketPort;
    }

    public String execute(UserId actor) {
        return ticketPort.issue(actor);
    }
}
