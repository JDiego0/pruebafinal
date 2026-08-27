package co.riwi.messaging.domain.error;

/**
 * Invalid credentials, expired/invalid token, or a detected refresh-token
 * reuse (AUTH-06). Maps to 401. Deliberately generic: the message is
 * identical for "unknown email" and "wrong password" so the response shape
 * and timing cannot be used to enumerate accounts.
 */
public class AuthenticationFailed extends DomainError {
    public AuthenticationFailed(String message) {
        super(message);
    }

    public static AuthenticationFailed invalidCredentials() {
        return new AuthenticationFailed("Invalid email or password.");
    }

    public static AuthenticationFailed tokenExpired() {
        return new AuthenticationFailed("Session expired, please log in again.");
    }

    public static AuthenticationFailed reuseDetected() {
        return new AuthenticationFailed("Refresh token reuse detected; all sessions revoked.");
    }
}
