package co.riwi.messaging.infrastructure.realtime;

import co.riwi.messaging.domain.model.UserId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * RT-01: one dedicated, non-pooled connection issuing LISTEN, distinct from
 * the Hikari pool used for everything else — holding a pooled connection
 * open indefinitely for LISTEN would starve the pool. AFTER-trigger payloads
 * (V7) carry ids only, never a message body (docs/ARCHITECTURE.md §5), so
 * this listener can never leak content even in principle; it only decides
 * WHO among the currently-connected clients should be told "something
 * changed in this channel — go fetch it through the normal RLS-protected
 * path" (RT-06).
 */
@Component
public class PostgresNotificationListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotificationListener.class);
    private static final List<String> CHANNELS = List.of("rw_message_created", "rw_message_updated", "rw_message_deleted");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final SseHub sseHub;
    private final JdbcTemplate pooledJdbc; // for the (unpooled) member-id lookup only

    private volatile boolean running = true;
    private Thread thread;

    public PostgresNotificationListener(
            @Value("${app.datasource.jdbc-url}") String jdbcUrl,
            @Value("${app.datasource.app-user}") String user,
            @Value("${app.datasource.app-password}") String password,
            SseHub sseHub, JdbcTemplate pooledJdbc
    ) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.sseHub = sseHub;
        this.pooledJdbc = pooledJdbc;
    }

    public synchronized void start() {
        if (thread == null) {
            thread = new Thread(this, "pg-notification-listener");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running) {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
                try (Statement statement = connection.createStatement()) {
                    for (String channel : CHANNELS) {
                        statement.execute("LISTEN " + channel);
                    }
                }
                log.info("Listening for realtime notifications on {}", CHANNELS);
                PGConnection pgConnection = connection.unwrap(PGConnection.class);

                while (running) {
                    PGNotification[] notifications = pgConnection.getNotifications(5_000);
                    if (notifications == null) {
                        continue;
                    }
                    for (PGNotification n : notifications) {
                        handle(n.getName(), n.getParameter());
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("Realtime listener connection lost, retrying in 5s: {}", e.getMessage());
                    sleepQuietly(5_000);
                }
            }
        }
    }

    private void handle(String channelName, String jsonPayload) {
        try {
            JsonNode payload = MAPPER.readTree(jsonPayload);
            UUID channelId = UUID.fromString(payload.get("channelId").asText());
            List<UUID> memberIds = pooledJdbc.query(
                    "SELECT rw_user_id FROM rw.rw_fn_list_channel_member_ids(?)",
                    (rs, rowNum) -> (UUID) rs.getObject("rw_user_id"), channelId
            );
            List<UserId> members = memberIds.stream().map(UserId::of).toList();
            String eventId = payload.has("messageId") ? payload.get("messageId").asText() : UUID.randomUUID().toString();
            sseHub.publish(members, eventName(channelName), eventId, payload);
        } catch (Exception e) {
            log.warn("Failed to process realtime notification on {}: {}", channelName, e.getMessage());
        }
    }

    private String eventName(String pgChannel) {
        return switch (pgChannel) {
            case "rw_message_created" -> "message.created";
            case "rw_message_updated" -> "message.updated";
            case "rw_message_deleted" -> "message.deleted";
            default -> pgChannel;
        };
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
