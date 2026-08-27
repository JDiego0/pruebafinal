package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.ChannelDetail;

import java.time.Instant;

public record ChannelResponse(
        String channelId, String type, String name, String slug, String topic,
        boolean isArchived, String createdBy, Instant createdAt
) {
    public static ChannelResponse from(ChannelDetail c) {
        return new ChannelResponse(c.channelId().toString(), c.type().code(), c.name(), c.slug(), c.topic(),
                c.archived(), c.createdBy().toString(), c.createdAt());
    }
}
