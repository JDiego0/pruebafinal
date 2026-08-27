package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.UserId;

import java.util.Optional;

/**
 * D-15: a single-use, short-lived ticket bound to an actor, so the SSE
 * stream endpoint (which {@code EventSource} cannot attach a bearer token
 * to) never needs a long-lived credential in its URL.
 */
public interface RealtimeTicketPort {

    String issue(UserId actor);

    /** Consumes the ticket — a second call with the same value returns empty. */
    Optional<UserId> consume(String ticket);
}
