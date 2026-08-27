package co.riwi.messaging.infrastructure.realtime;

import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.RealtimeTicketPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, single-use, 60-second tickets (D-15). Adequate for this MVP's
 * single-instance target, same scope decision as {@code RateLimitFilter} — a
 * multi-instance deployment would need a shared store instead.
 */
@Component
public class InMemoryTicketService implements RealtimeTicketPort {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long TTL_SECONDS = 60;

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    private record Entry(UserId userId, Instant expiresAt) {
    }

    @Override
    public String issue(UserId actor) {
        purgeExpired();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Entry(actor, Instant.now().plusSeconds(TTL_SECONDS)));
        return ticket;
    }

    @Override
    public Optional<UserId> consume(String ticket) {
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tickets.values().removeIf(e -> e.expiresAt().isBefore(now));
    }
}
