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

    @Bean
    public ListConversationsUseCase listConversationsUseCase(ConversationRepository conversationRepository, TransactionRunner transactionRunner) {
        return new ListConversationsUseCase(conversationRepository, transactionRunner);
    }

    @Bean
    public DiscoverChannelsUseCase discoverChannelsUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new DiscoverChannelsUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public CreateChannelUseCase createChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new CreateChannelUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public OpenDirectChannelUseCase openDirectChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new OpenDirectChannelUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public GetChannelUseCase getChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new GetChannelUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public UpdateChannelUseCase updateChannelUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new UpdateChannelUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public ListChannelMembersUseCase listChannelMembersUseCase(ChannelRepository channelRepository, TransactionRunner transactionRunner) {
        return new ListChannelMembersUseCase(channelRepository, transactionRunner);
    }

    @Bean
    public JoinChannelUseCase joinChannelUseCase(ChannelRepository channelRepository, ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        return new JoinChannelUseCase(channelRepository, membershipPort, transactionRunner);
    }

    @Bean
    public AddChannelMemberUseCase addChannelMemberUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        return new AddChannelMemberUseCase(membershipPort, transactionRunner);
    }

    @Bean
    public LeaveChannelUseCase leaveChannelUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        return new LeaveChannelUseCase(membershipPort, transactionRunner);
    }

    @Bean
    public RemoveChannelMemberUseCase removeChannelMemberUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        return new RemoveChannelMemberUseCase(membershipPort, transactionRunner);
    }

    @Bean
    public UpdateReadCursorUseCase updateReadCursorUseCase(ChannelMembershipPort membershipPort, TransactionRunner transactionRunner) {
        return new UpdateReadCursorUseCase(membershipPort, transactionRunner);
    }

    @Bean
    public GetMessageHistoryUseCase getMessageHistoryUseCase(ChannelRepository channelRepository, MessageRepository messageRepository, TransactionRunner transactionRunner) {
        return new GetMessageHistoryUseCase(channelRepository, messageRepository, transactionRunner);
    }

    @Bean
    public SendMessageUseCase sendMessageUseCase(ChannelRepository channelRepository, MessageRepository messageRepository, TransactionRunner transactionRunner) {
        return new SendMessageUseCase(channelRepository, messageRepository, transactionRunner);
    }

    @Bean
    public EditMessageUseCase editMessageUseCase(MessageRepository messageRepository, TransactionRunner transactionRunner) {
        return new EditMessageUseCase(messageRepository, transactionRunner);
    }

    @Bean
    public DeleteMessageUseCase deleteMessageUseCase(MessageRepository messageRepository, TransactionRunner transactionRunner) {
        return new DeleteMessageUseCase(messageRepository, transactionRunner);
    }
}
