package co.riwi.messaging.domain.model;

import java.time.Instant;

public record ChannelMemberSummary(
        UserId userId,
        String displayName,
        String avatarUrl,
        ChannelMemberRole role,
        Instant joinedAt
) {
}
