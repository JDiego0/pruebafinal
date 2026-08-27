package co.riwi.messaging.application.result;

import co.riwi.messaging.domain.model.UserProfile;

/**
 * {@code rawRefreshToken} is the ONLY place the plaintext refresh token ever
 * exists outside the client's cookie — the use case returns it once so the
 * controller can set the HttpOnly cookie; nothing downstream logs or
 * persists it (only its hash is ever stored, AUTH-04).
 */
public record SessionResult(
        String accessToken,
        long accessTokenExpiresInSeconds,
        String rawRefreshToken,
        long refreshTokenExpiresInSeconds,
        UserProfile user
) {
}
