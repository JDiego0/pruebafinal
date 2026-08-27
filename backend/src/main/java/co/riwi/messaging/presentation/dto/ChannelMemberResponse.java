package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.ChannelMemberSummary;

import java.time.Instant;

public record ChannelMemberResponse(String userId, String displayName, String avatarUrl, String role, Instant joinedAt) {
    public static ChannelMemberResponse from(ChannelMemberSummary m) {
        return new ChannelMemberResponse(m.userId().toString(), m.displayName(), m.avatarUrl(), m.role().code(), m.joinedAt());
    }
}
