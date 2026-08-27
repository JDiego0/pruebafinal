package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.model.UserProfile;

import java.time.Instant;

public record UserProfileResponse(
        String userId,
        String email,
        String displayName,
        String role,
        String locale,
        String avatarUrl,
        Instant createdAt,
        Instant lastSeenAt
) {
    public static UserProfileResponse from(UserProfile p) {
        return new UserProfileResponse(
                p.userId().toString(), p.email().value(), p.displayName().value(),
                p.role().code(), p.locale().code(), p.avatarUrl(), p.createdAt(), p.lastSeenAt()
        );
    }
}
