package co.riwi.messaging.infrastructure.config;

import co.riwi.messaging.infrastructure.realtime.PostgresNotificationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the LISTEN thread only once the application context is fully up
 * (ApplicationReadyEvent), not during bean construction — avoids racing
 * against datasource/bean initialization order.
 * <p>
 * Excluded under the {@code seed} profile: that one-shot run has no clients
 * to notify, and a background LISTEN thread on a raw (non-pooled) JDBC
 * connection was found to prevent the seed process from exiting cleanly
 * even though the thread is daemon — a real gap, not a style choice.
 */
@Configuration
@EnableScheduling // backs SseHub's heartbeat
@Profile("!seed")
public class RealtimeConfig {

    private final PostgresNotificationListener listener;

    public RealtimeConfig(PostgresNotificationListener listener) {
        this.listener = listener;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        listener.start();
    }
}
