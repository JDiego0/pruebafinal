package co.riwi.messaging.domain.port;

import java.time.Instant;

/** Seam for deterministic tests; production returns {@link Instant#now()}. */
public interface Clock {
    Instant now();
}
