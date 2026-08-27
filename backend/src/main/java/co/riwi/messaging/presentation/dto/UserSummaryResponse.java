package co.riwi.messaging.presentation.dto;

import co.riwi.messaging.domain.port.UserRepository.UserQueryRow;

public record UserSummaryResponse(String userId, String displayName, String email, String avatarUrl, String role, String locale) {
    public static UserSummaryResponse from(UserQueryRow row) {
        return new UserSummaryResponse(
                row.userId().toString(), row.displayName().value(), row.email(),
                row.avatarUrl(), row.roleCode(), row.locale().code()
        );
    }
}
