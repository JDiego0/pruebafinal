package co.riwi.messaging.infrastructure.web;

import co.riwi.messaging.domain.error.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * OBJ-04/API-07: opaque keyset cursor over (created_at, message_id) — never
 * an OFFSET (MSG-04). Opaque to the client by construction: the only way to
 * get one is from a previous response's pageInfo.nextCursor.
 */
public final class MessageCursor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageCursor() {
    }

    public record Decoded(Instant createdAt, long messageId) {
    }

    public static String encode(Instant createdAt, long messageId) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("ts", createdAt.toString(), "id", messageId));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode cursor", e);
        }
    }

    public static Decoded decode(String cursor) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            Map<?, ?> map = MAPPER.readValue(json, Map.class);
            return new Decoded(Instant.parse((String) map.get("ts")), ((Number) map.get("id")).longValue());
        } catch (Exception e) {
            throw new ValidationError("cursor", "invalid or corrupted pagination cursor");
        }
    }
}
