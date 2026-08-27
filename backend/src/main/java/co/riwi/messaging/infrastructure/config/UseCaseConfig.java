package co.riwi.messaging.infrastructure.config;

import co.riwi.messaging.application.usecase.*;
import co.riwi.messaging.domain.port.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ARCH-05: use cases are plain classes with no framework dependency of their
 * own (see LoginUseCase etc.) — this is the one place Spring wires them,
 * passing configuration as plain values so the application layer never
 * imports {@code @Value} or any other Spring annotation itself.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public LoginUseCase loginUseCase(
            CredentialPort credentialPort, PasswordHasher passwordHasher, UserRepository userRepository,
            RefreshTokenPort refreshTokenPort, AccessTokenPort accessTokenPort, TokenHasher tokenHasher,
            TransactionRunner transactionRunner, Clock clock,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds
    ) {
        return new LoginUseCase(credentialPort, passwordHasher, userRepository, refreshTokenPort,
                accessTokenPort, tokenHasher, transactionRunner, clock, refreshTokenTtlSeconds);
    }

    @Bean
    public RefreshSessionUseCase refreshSessionUseCase(
            RefreshTokenPort refreshTokenPort, UserRepository userRepository, AccessTokenPort accessTokenPort,
            TokenHasher tokenHasher, TransactionRunner transactionRunner, Clock clock,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds
    ) {
        return new RefreshSessionUseCase(refreshTokenPort, userRepository, accessTokenPort,
                tokenHasher, transactionRunner, clock, refreshTokenTtlSeconds);
    }

    @Bean
    public LogoutUseCase logoutUseCase(RefreshTokenPort refreshTokenPort, TokenHasher tokenHasher, TransactionRunner transactionRunner) {
        return new LogoutUseCase(refreshTokenPort, tokenHasher, transactionRunner);
    }

    @Bean
    public GetProfileUseCase getProfileUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        return new GetProfileUseCase(userRepository, transactionRunner);
    }

    @Bean
    public UpdateProfileUseCase updateProfileUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        return new UpdateProfileUseCase(userRepository, transactionRunner);
    }

    @Bean
    public QueryUsersUseCase queryUsersUseCase(UserRepository userRepository, TransactionRunner transactionRunner) {
        return new QueryUsersUseCase(userRepository, transactionRunner);
    }

    @Bean
    public ModifyUserUseCase modifyUserUseCase(AdminUserPort adminUserPort, TransactionRunner transactionRunner) {
        return new ModifyUserUseCase(adminUserPort, transactionRunner);
    }
}
