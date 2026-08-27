package co.riwi.messaging.application.usecase;

import co.riwi.messaging.domain.port.RefreshTokenPort;
import co.riwi.messaging.domain.port.TokenHasher;
import co.riwi.messaging.domain.port.TransactionRunner;

/** AUTH-07: revokes the presented token's whole family. Idempotent and silent on an already-invalid token. */
public class LogoutUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final TokenHasher tokenHasher;
    private final TransactionRunner transactionRunner;

    public LogoutUseCase(RefreshTokenPort refreshTokenPort, TokenHasher tokenHasher, TransactionRunner transactionRunner) {
        this.refreshTokenPort = refreshTokenPort;
        this.tokenHasher = tokenHasher;
        this.transactionRunner = transactionRunner;
    }

    public void execute(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        byte[] hash = tokenHasher.hash(rawRefreshToken);
        transactionRunner.runAnonymous(() -> refreshTokenPort.revokeByTokenHash(hash, "LOGOUT"));
    }
}
