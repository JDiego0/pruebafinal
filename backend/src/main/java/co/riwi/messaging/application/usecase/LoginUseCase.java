package co.riwi.messaging.application.usecase;

import co.riwi.messaging.application.command.LoginCommand;
import co.riwi.messaging.application.result.SessionResult;
import co.riwi.messaging.domain.error.AuthenticationFailed;
import co.riwi.messaging.domain.model.*;
import co.riwi.messaging.domain.port.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * AUTH-01/AUTH-02: verify credentials, issue an access token and a fresh
 * refresh-token family. The response is identical in shape and (as much as
 * JVM timing allows) in cost whether the email is unknown or the password is
 * wrong — {@link AuthenticationFailed#invalidCredentials()} either way, so
 * neither the error nor its timing can enumerate accounts.
 */
public class LoginUseCase {

    private final CredentialPort credentialPort;
    private final PasswordHasher passwordHasher;
    private final UserRepository userRepository;
    private final RefreshTokenPort refreshTokenPort;
    private final AccessTokenPort accessTokenPort;
    private final TokenHasher tokenHasher;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final long refreshTokenTtlSeconds;

    public LoginUseCase(
            CredentialPort credentialPort, PasswordHasher passwordHasher, UserRepository userRepository,
            RefreshTokenPort refreshTokenPort, AccessTokenPort accessTokenPort, TokenHasher tokenHasher,
            TransactionRunner transactionRunner, Clock clock, long refreshTokenTtlSeconds
    ) {
        this.credentialPort = credentialPort;
        this.passwordHasher = passwordHasher;
        this.userRepository = userRepository;
        this.refreshTokenPort = refreshTokenPort;
        this.accessTokenPort = accessTokenPort;
        this.tokenHasher = tokenHasher;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public SessionResult execute(LoginCommand command) {
        // 1. validate
        Email email = Email.of(command.email());
        if (command.rawPassword() == null || command.rawPassword().isBlank()) {
            throw AuthenticationFailed.invalidCredentials();
        }

        // 2. invoke: credential lookup runs with no actor (none exists yet)
        Optional<CredentialLookup> credential = transactionRunner.runAnonymous(() -> credentialPort.findForLogin(email));

        if (credential.isEmpty()) {
            throw AuthenticationFailed.invalidCredentials();
        }
        CredentialLookup found = credential.get();

        boolean locked = found.lockedUntil() != null && found.lockedUntil().isAfter(clock.now());
        boolean passwordOk = !locked && found.active() && passwordHasher.matches(command.rawPassword(), found.passwordHash());

        transactionRunner.runAnonymous(() -> {
            credentialPort.recordLoginAttempt(found.userId(), passwordOk);
            return null;
        });

        if (!passwordOk) {
            throw AuthenticationFailed.invalidCredentials();
        }

        // 3. now that the actor is known and legitimate, fetch the profile as them
        UserProfile profile = transactionRunner.runAsActor(found.userId(), () -> userRepository.findById(found.userId()))
                .orElseThrow(() -> new IllegalStateException("authenticated user has no visible profile row"));

        // 4. issue tokens
        String accessToken = accessTokenPort.issue(profile.userId(), profile.role(), profile.displayName(), profile.locale());

        String rawRefreshToken = tokenHasher.generateOpaqueToken();
        byte[] tokenHash = tokenHasher.hash(rawRefreshToken);
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = clock.now().plus(refreshTokenTtlSeconds, ChronoUnit.SECONDS);

        transactionRunner.runAnonymous(() -> {
            refreshTokenPort.issue(profile.userId(), familyId, tokenHash, expiresAt, command.userAgent(), command.ipAddress());
            return null;
        });

        return new SessionResult(accessToken, accessTokenPort.ttlSeconds(), rawRefreshToken, refreshTokenTtlSeconds, profile);
    }
}
