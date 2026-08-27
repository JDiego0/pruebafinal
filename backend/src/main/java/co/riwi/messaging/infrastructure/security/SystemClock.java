package co.riwi.messaging.infrastructure.security;

import co.riwi.messaging.domain.port.Clock;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SystemClock implements Clock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
