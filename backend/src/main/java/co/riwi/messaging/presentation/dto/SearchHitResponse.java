package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.SearchHit;

import java.time.Instant;

public record SearchHitResponse(long messageId, String channelId, String senderId, Instant createdAt, String highlight, float rank) {
    public static SearchHitResponse from(SearchHit h) {
        return new SearchHitResponse(h.messageId().value(), h.channelId().toString(), h.senderId().toString(), h.createdAt(), h.highlight(), h.rank());
    }
}
