package co.riwi.messaging.presentation.controller;

import co.riwi.messaging.application.command.LoginCommand;
import co.riwi.messaging.application.result.SessionResult;
import co.riwi.messaging.application.usecase.LoginUseCase;
import co.riwi.messaging.application.usecase.LogoutUseCase;
import co.riwi.messaging.application.usecase.RefreshSessionUseCase;
import co.riwi.messaging.presentation.dto.LoginRequest;
import co.riwi.messaging.presentation.dto.LoginResponse;
import co.riwi.messaging.presentation.dto.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AUTH-02..AUTH-08. The refresh token never appears in a JSON body — only as
 * an HttpOnly cookie (D-15) — and its raw value is discarded by this class
 * the instant the cookie is built; nothing here logs it.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String COOKIE_NAME = "rw_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final LoginUseCase loginUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;
    private final LogoutUseCase logoutUseCase;
    private final boolean cookieSecure;

    public AuthController(LoginUseCase loginUseCase, RefreshSessionUseCase refreshSessionUseCase,
                           LogoutUseCase logoutUseCase, @Value("${app.cookie.secure}") boolean cookieSecure) {
        this.loginUseCase = loginUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.logoutUseCase = logoutUseCase;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionResult result = loginUseCase.execute(new LoginCommand(
                request.email(), request.password(),
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr()
        ));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken(), result.refreshTokenExpiresInSeconds()).toString())
                .body(toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        SessionResult result = refreshSessionUseCase.execute(
                refreshToken, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken(), result.refreshTokenExpiresInSeconds()).toString())
                .body(toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_NAME, required = false) String refreshToken) {
        logoutUseCase.execute(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    private LoginResponse toResponse(SessionResult result) {
        return new LoginResponse(result.accessToken(), "Bearer", result.accessTokenExpiresInSeconds(),
                UserProfileResponse.from(result.user()));
    }

    private ResponseCookie refreshCookie(String rawToken, long ttlSeconds) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(ttlSeconds)
                .build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
