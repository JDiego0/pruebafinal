package co.riwi.messaging.infrastructure.web;

import co.riwi.messaging.domain.error.ValidationError;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** SRCH-04: opaque keyset cursor over (rank, message_id) — no OFFSET (MSG-04). */
public final class SearchCursor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SearchCursor() {
    }

    public record Decoded(float rank, long messageId) {
    }

    public static String encode(float rank, long messageId) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("rank", rank, "id", messageId));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode cursor", e);
        }
    }

    public static Decoded decode(String cursor) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            Map<?, ?> map = MAPPER.readValue(json, Map.class);
            return new Decoded(((Number) map.get("rank")).floatValue(), ((Number) map.get("id")).longValue());
        } catch (Exception e) {
            throw new ValidationError("cursor", "invalid or corrupted pagination cursor");
        }
    }
}
