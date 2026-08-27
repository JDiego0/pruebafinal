package co.riwi.messaging.domain.model;

import java.time.Instant;

public record UserProfile(
        UserId userId,
        Email email,
        DisplayName displayName,
        Role role,
        Locale locale,
        String avatarUrl,
        boolean active,
        Instant createdAt,
        Instant lastSeenAt
) {
}
