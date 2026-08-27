package co.riwi.messaging.presentation.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserProfileResponse user) {
}
