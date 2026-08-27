package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.result.SessionResult;
import co.riwi.messaging.domain.error.AuthenticationFailed;
import co.riwi.messaging.domain.model.RefreshTokenRotation;
import co.riwi.messaging.domain.model.UserProfile;
import co.riwi.messaging.domain.port.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * AUTH-05/AUTH-06: rotate-on-use with reuse detection, both inside the one
 * transaction {@code rw_fn_rotate_refresh_token} runs in (V4). The presented
 * token's hash is the ONLY thing that authorizes this call — there is no
 * user id parameter to spoof.
 */
public class RefreshSessionUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final UserRepository userRepository;
    private final AccessTokenPort accessTokenPort;
    private final TokenHasher tokenHasher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final long refreshTokenTtlSeconds;

    public RefreshSessionUseCase(
            RefreshTokenPort refreshTokenPort, UserRepository userRepository, AccessTokenPort accessTokenPort,
            TokenHasher tokenHasher, TransactionRunner transactionRunner, Clock clock, long refreshTokenTtlSeconds
    ) {
        this.refreshTokenPort = refreshTokenPort;
        this.userRepository = userRepository;
        this.accessTokenPort = accessTokenPort;
        this.tokenHasher = tokenHasher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public SessionResult execute(String presentedRawToken, String userAgent, String ipAddress) {
        if (presentedRawToken == null || presentedRawToken.isBlank()) {
            throw AuthenticationFailed.tokenExpired();
        }

        byte[] presentedHash = tokenHasher.hash(presentedRawToken);
        String newRawToken = tokenHasher.generateOpaqueToken();
        byte[] newHash = tokenHasher.hash(newRawToken);
        Instant newExpiresAt = clock.now().plus(refreshTokenTtlSeconds, ChronoUnit.SECONDS);

        RefreshTokenRotation rotation = transactionRunner.runAnonymous(() ->
                refreshTokenPort.rotate(presentedHash, newHash, newExpiresAt, userAgent, ipAddress));

        switch (rotation.status()) {
            case REUSE_DETECTED -> throw AuthenticationFailed.reuseDetected();
            case EXPIRED, NOT_FOUND -> throw AuthenticationFailed.tokenExpired();
            case ROTATED -> { /* fall through */ }
        }

        UserProfile profile = transactionRunner.runAsActor(rotation.userId(), () -> userRepository.findById(rotation.userId()))
                .orElseThrow(() -> new IllegalStateException("rotated token's user has no visible profile row"));

        String accessToken = accessTokenPort.issue(profile.userId(), profile.role(), profile.displayName(), profile.locale());

        return new SessionResult(accessToken, accessTokenPort.ttlSeconds(), newRawToken, refreshTokenTtlSeconds, profile);
    }
}
