package co.riwi.messaging.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ChannelId(UUID value) {

    public ChannelId {
        Objects.requireNonNull(value, "channel id must not be null");
    }

    public static ChannelId of(UUID value) {
        return new ChannelId(value);
    }

    public static ChannelId of(String value) {
        return new ChannelId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
