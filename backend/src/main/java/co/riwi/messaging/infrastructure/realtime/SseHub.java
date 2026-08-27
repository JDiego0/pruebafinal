package co.riwi.messaging.infrastructure.realtime;

import co.riwi.messaging.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-process fan-out (D-05). Every backend instance runs its own
 * {@link PostgresNotificationListener} and its own hub, so multi-instance
 * fan-out works with no broker: each instance independently receives every
 * NOTIFY and pushes to whichever of ITS OWN connected clients are members —
 * membership is re-checked per delivery (RT-06), not cached at connect time,
 * so a user who leaves a channel stops receiving its events immediately
 * (IBR-07/IBR-09).
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long HEARTBEAT_MS = 20_000;

    private final Map<UserId, Set<SseEmitter>> byUser = new ConcurrentHashMap<>();

    public SseEmitter register(UserId userId) {
        SseEmitter emitter = new SseEmitter(0L); // no server-side timeout; the client controls the connection lifetime
        Set<SseEmitter> emitters = byUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);

        Runnable cleanup = () -> {
            Set<SseEmitter> set = byUser.get(userId);
            if (set != null) {
                set.remove(emitter);
                if (set.isEmpty()) {
                    byUser.remove(userId, set);
                }
            }
        };
        emitter.onCompletion(cleanup::run);
        emitter.onTimeout(cleanup::run);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    /** Pushes to exactly the currently-connected members of {@code memberIds} — a stale local set is never trusted. */
    public void publish(List<UserId> memberIds, String eventName, String eventId, Object payload) {
        for (UserId memberId : memberIds) {
            Set<SseEmitter> emitters = byUser.get(memberId);
            if (emitters == null || emitters.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().id(eventId).name(eventName).data(payload));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    emitters.remove(emitter);
                }
            }
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        byUser.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    emitters.remove(emitter);
                }
            }
        });
    }

    public int connectionCount() {
        return byUser.values().stream().mapToInt(Set::size).sum();
    }
}
