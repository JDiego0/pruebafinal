package co.riwi.messaging.domain.model;

import java.time.Instant;

public record ChannelDetail(
        ChannelId channelId,
        ChannelType type,
        String name,
        String slug,
        String topic,
        boolean archived,
        UserId createdBy,
        Instant createdAt
) {
}
