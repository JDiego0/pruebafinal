package co.riwi.messaging;

import co.riwi.messaging.application.command.CreateChannelCommand;
import co.riwi.messaging.application.command.LoginCommand;
import co.riwi.messaging.application.command.SendMessageCommand;
import co.riwi.messaging.application.result.SessionResult;
import co.riwi.messaging.application.usecase.CreateChannelUseCase;
import co.riwi.messaging.application.usecase.DeleteMessageUseCase;
import co.riwi.messaging.application.usecase.EditMessageUseCase;
import co.riwi.messaging.application.usecase.GetMessageHistoryUseCase;
import co.riwi.messaging.application.usecase.JoinChannelUseCase;
import co.riwi.messaging.application.usecase.LoginUseCase;
import co.riwi.messaging.application.usecase.OpenDirectChannelUseCase;
import co.riwi.messaging.application.usecase.SearchMessagesUseCase;
import co.riwi.messaging.application.usecase.SendMessageUseCase;
import co.riwi.messaging.domain.error.AuthenticationFailed;
import co.riwi.messaging.domain.error.ResourceNotVisible;
import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.MessageRecord;
import co.riwi.messaging.domain.model.RagCandidate;
import co.riwi.messaging.domain.model.SearchHit;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import co.riwi.messaging.domain.port.PasswordHasher;
import co.riwi.messaging.domain.port.RagRepository;
import co.riwi.messaging.domain.port.TransactionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The mandatory PostgreSQL security suite (TEST-01..TEST-11).
 *
 * <p>Every assertion runs against a real PostgreSQL server with the production
 * migrations applied, and the application connects as {@code rw_app}
 * (NOSUPERUSER, NOBYPASSRLS) — the same identity the running backend uses.
 * Nothing here is stubbed: when a row is not returned it is because the
 * database refused to return it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RlsSecurityIT {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeAll
    static void migrate() {
        PostgresTestSupport.migrateAndSeedCatalogues();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        PostgresTestSupport.migrateAndSeedCatalogues();
        registry.add("app.datasource.jdbc-url", PostgresTestSupport::jdbcUrl);
        registry.add("app.datasource.app-user", () -> "rw_app");
        registry.add("app.datasource.app-password", () -> PostgresTestSupport.APP_PASSWORD);
        registry.add("app.datasource.migrator-user", () -> "rw_migrator");
        registry.add("app.datasource.migrator-password", () -> PostgresTestSupport.MIGRATOR_PASSWORD);
        registry.add("app.jwt.secret", () -> "dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionRunner transactionRunner;
    @Autowired PasswordHasher passwordHasher;
    @Autowired EmbeddingProvider embeddingProvider;
    @Autowired RagRepository ragRepository;
    @Autowired CreateChannelUseCase createChannelUseCase;
    @Autowired OpenDirectChannelUseCase openDirectChannelUseCase;
    @Autowired JoinChannelUseCase joinChannelUseCase;
    @Autowired SendMessageUseCase sendMessageUseCase;
    @Autowired EditMessageUseCase editMessageUseCase;
    @Autowired DeleteMessageUseCase deleteMessageUseCase;
    @Autowired GetMessageHistoryUseCase getMessageHistoryUseCase;
    @Autowired SearchMessagesUseCase searchMessagesUseCase;
    @Autowired LoginUseCase loginUseCase;

    // ── SEC-01 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-01: a non-member gets nothing from a private channel history")
    void nonMemberCannotReadPrivateChannelHistory() {
        UserId owner = createUser("owner");
        UserId outsider = createUser("outsider");

        ChannelId channel = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PRIVATE", "Board room", slug("board"), "Confidential"));
        sendMessageUseCase.execute(owner, channel,
                new SendMessageCommand("Acquisition closes on Friday", UUID.randomUUID(), null));

        assertThat(getMessageHistoryUseCase.execute(owner, channel, null, null, 50)).hasSize(1);

        assertThatThrownBy(() -> getMessageHistoryUseCase.execute(outsider, channel, null, null, 50))
                .isInstanceOf(ResourceNotVisible.class);

        // The same question asked straight at the table, as the outsider: still zero rows.
        Integer visible = transactionRunner.runAsActor(outsider, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rw.rw_messages WHERE rw_channel_id = ?", Integer.class, channel.value()));
        assertThat(visible).isZero();
    }

    // ── SEC-02 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-02: a third party gets nothing from someone elses direct channel")
    void thirdPartyCannotReadDirectChannel() {
        UserId a = createUser("dm-a");
        UserId b = createUser("dm-b");
        UserId c = createUser("dm-c");

        ChannelId dm = openDirectChannelUseCase.execute(a, b).channelId();
        sendMessageUseCase.execute(a, dm, new SendMessageCommand("Just between us", UUID.randomUUID(), null));

        assertThat(getMessageHistoryUseCase.execute(b, dm, null, null, 50)).hasSize(1);
        assertThatThrownBy(() -> getMessageHistoryUseCase.execute(c, dm, null, null, 50))
                .isInstanceOf(ResourceNotVisible.class);
    }

    // ── SEC-04 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-04: full-text search never returns a message the actor cannot read")
    void searchDoesNotLeakUnauthorizedMessages() {
        UserId owner = createUser("search-owner");
        UserId outsider = createUser("search-outsider");

        ChannelId privateChannel = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PRIVATE", "Kryptonite", slug("krypto"), "Weakness"));
        sendMessageUseCase.execute(owner, privateChannel,
                new SendMessageCommand("kryptonite is our only weakness", UUID.randomUUID(), null));

        List<SearchHit> ownerHits = searchMessagesUseCase.execute(owner, "kryptonite", null, null, null, 10);
        assertThat(ownerHits).isNotEmpty();

        List<SearchHit> outsiderHits = searchMessagesUseCase.execute(outsider, "kryptonite", null, null, null, 10);
        assertThat(outsiderHits).isEmpty();
    }

    // ── SEC-06 / AI-08 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-05: RAG retrieval returns only messages the actor is authorized to read")
    void ragRetrievalIsRlsScoped() {
        UserId owner = createUser("rag-owner");
        UserId outsider = createUser("rag-outsider");

        ChannelId open = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PUBLIC", "Release notes", slug("release"), "Shipping"));
        ChannelId secret = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PRIVATE", "Severance", slug("severance"), "Confidential"));
        joinChannelUseCase.execute(outsider, open);

        String sharedText = "the Thursday ten o clock demo";
        MessageId publicMessage = sendMessageUseCase.execute(owner, open,
                new SendMessageCommand(sharedText, UUID.randomUUID(), null)).messageId();
        MessageId secretMessage = sendMessageUseCase.execute(owner, secret,
                new SendMessageCommand(sharedText, UUID.randomUUID(), null)).messageId();

        float[] vector = embeddingProvider.embed(List.of(sharedText)).get(0);
        storeEmbedding(publicMessage, vector);
        storeEmbedding(secretMessage, vector);

        List<RagCandidate> ownerCandidates = transactionRunner.runAsActor(owner,
                () -> ragRepository.retrieveCandidates(vector, null, 50, 0.0));
        assertThat(ownerCandidates).extracting(c -> c.messageId().value())
                .contains(publicMessage.value(), secretMessage.value());

        List<RagCandidate> outsiderCandidates = transactionRunner.runAsActor(outsider,
                () -> ragRepository.retrieveCandidates(vector, null, 50, 0.0));
        assertThat(outsiderCandidates).extracting(c -> c.messageId().value())
                .contains(publicMessage.value())
                .doesNotContain(secretMessage.value());
    }

    @Test
    @DisplayName("TEST-12: a question resembling private text retrieves it for a member and nothing for an outsider")
    void ragRetrievalRanksByMeaningAndStillRefusesOutsiders() {
        UserId member = createUser("lex-member");
        UserId outsider = createUser("lex-outsider");

        ChannelId open = createChannelUseCase.execute(member,
                new CreateChannelCommand("PUBLIC", "Standup", slug("standup"), "Daily"));
        ChannelId secret = createChannelUseCase.execute(member,
                new CreateChannelCommand("PRIVATE", "War room", slug("warroom"), "Confidential"));
        joinChannelUseCase.execute(outsider, open);

        MessageId publicMessage = sendMessageUseCase.execute(member, open,
                new SendMessageCommand("The quarterly roadmap review is on Tuesday morning",
                        UUID.randomUUID(), null)).messageId();
        MessageId secretMessage = sendMessageUseCase.execute(member, secret,
                new SendMessageCommand("The quarterly roadmap review was moved because of the outage",
                        UUID.randomUUID(), null)).messageId();

        // The question is never identical to either message — retrieval has to
        // rank on shared vocabulary, which is what the offline provider gives us.
        float[] question = embeddingProvider.embed(List.of("when is the quarterly roadmap review")).get(0);
        storeEmbedding(publicMessage, embeddingProvider.embed(List.of(
                "The quarterly roadmap review is on Tuesday morning")).get(0));
        storeEmbedding(secretMessage, embeddingProvider.embed(List.of(
                "The quarterly roadmap review was moved because of the outage")).get(0));

        List<RagCandidate> memberView = transactionRunner.runAsActor(member,
                () -> ragRepository.retrieveCandidates(question, null, 20, 0.01));
        assertThat(memberView)
                .as("a lexically related question must actually retrieve something")
                .isNotEmpty();
        assertThat(memberView).extracting(c -> c.messageId().value())
                .contains(publicMessage.value(), secretMessage.value());

        List<RagCandidate> outsiderView = transactionRunner.runAsActor(outsider,
                () -> ragRepository.retrieveCandidates(question, null, 20, 0.01));
        assertThat(outsiderView).extracting(c -> c.messageId().value())
                .contains(publicMessage.value())
                .doesNotContain(secretMessage.value());
    }

    // ── SEC-11 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-06: rw_app is NOSUPERUSER and NOBYPASSRLS")
    void applicationRoleCannotBypassRls() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = 'rw_app'", Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'rw_app'", Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_user", String.class)).isEqualTo("rw_app");
    }

    // ── SEC-13 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-07: with no actor bound, user content is invisible")
    void unsetActorSeesNothing() {
        UserId owner = createUser("anon-probe");
        ChannelId channel = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PUBLIC", "Anon probe", slug("anon"), "Probe"));
        sendMessageUseCase.execute(owner, channel,
                new SendMessageCommand("visible to members", UUID.randomUUID(), null));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM rw.rw_messages", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM rw.rw_channels", Integer.class)).isZero();
    }

    // ── MSG-01 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-08: messages can never be physically deleted")
    void physicalDeleteIsImpossible() {
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM rw.rw_messages"))
                .rootCause()
                .hasMessageContaining("permission denied");

        Integer deletePolicies = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname = 'rw' AND tablename = 'rw_messages' AND cmd = 'DELETE'",
                Integer.class);
        assertThat(deletePolicies).isZero();

        Integer blockTrigger = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'rw_trg_messages_block_delete'", Integer.class);
        assertThat(blockTrigger).isEqualTo(1);
    }

    // ── AUTH ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-09: login issues a session for the right password and refuses the wrong one")
    void authenticationWorks() {
        UserId user = createUser("auth");
        String email = "auth-" + user.value() + "@riwi.co";
        setPassword(user, email, "Correct-Horse-Battery-9");

        SessionResult session = loginUseCase.execute(
                new LoginCommand(email, "Correct-Horse-Battery-9", "junit", "127.0.0.1"));
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.rawRefreshToken()).isNotBlank();
        assertThat(session.user().userId().value()).isEqualTo(user.value());

        assertThatThrownBy(() -> loginUseCase.execute(
                new LoginCommand(email, "wrong-password", "junit", "127.0.0.1")))
                .isInstanceOf(AuthenticationFailed.class);
    }

    // ── MSG-02, MSG-04 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-10: edit keeps history, delete is logical, pagination is keyset")
    void editDeleteAndKeysetPagination() {
        UserId owner = createUser("edit-owner");
        ChannelId channel = createChannelUseCase.execute(owner,
                new CreateChannelCommand("PUBLIC", "Edits", slug("edits"), "Editing"));

        for (int i = 0; i < 5; i++) {
            sendMessageUseCase.execute(owner, channel,
                    new SendMessageCommand("message " + i, UUID.randomUUID(), null));
        }

        List<MessageRecord> firstPage = getMessageHistoryUseCase.execute(owner, channel, null, null, 2);
        assertThat(firstPage).hasSize(2);
        MessageRecord last = firstPage.get(firstPage.size() - 1);
        List<MessageRecord> secondPage = getMessageHistoryUseCase.execute(
                owner, channel, last.createdAt(), last.messageId(), 2);
        assertThat(secondPage).hasSize(2);
        assertThat(secondPage).extracting(m -> m.messageId().value())
                .doesNotContainAnyElementsOf(firstPage.stream().map(m -> m.messageId().value()).toList());

        MessageId target = firstPage.get(0).messageId();
        editMessageUseCase.execute(owner, target, "message 4 (corrected)");
        MessageRecord edited = getMessageHistoryUseCase.execute(owner, channel, null, null, 1).get(0);
        assertThat(edited.body()).isEqualTo("message 4 (corrected)");
        assertThat(edited.revision()).isEqualTo(2);
        assertThat(edited.editedAt()).isNotNull();

        Integer editRows = transactionRunner.runAsActor(owner, () -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rw.rw_message_edits WHERE rw_message_id = ?", Integer.class, target.value()));
        assertThat(editRows).isEqualTo(1);

        deleteMessageUseCase.execute(owner, target);
        MessageRecord tombstone = getMessageHistoryUseCase.execute(owner, channel, null, null, 1).get(0);
        assertThat(tombstone.deleted()).isTrue();
        assertThat(tombstone.body()).isNull();

        Long stillThere = PostgresTestSupport.migratorTemplate().queryForObject(
                "SELECT count(*) FROM rw.rw_messages WHERE rw_message_id = ?", Long.class, target.value());
        assertThat(stillThere).isEqualTo(1L);
    }

    // ── TEST-11 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TEST-11: RLS is enabled and forced on every table in the rw schema")
    void rlsIsEnabledAndForcedEverywhere() {
        List<String> unprotected = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'rw' AND c.relkind = 'r'
                  AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity)
                ORDER BY 1
                """, String.class);
        assertThat(unprotected)
                .as("every table in the rw schema must have RLS enabled AND forced")
                .isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String slug(String base) {
        return base + "-" + SEQ.incrementAndGet();
    }

    private UserId createUser(String prefix) {
        UUID id = UUID.randomUUID();
        PostgresTestSupport.migratorTemplate().update("""
                INSERT INTO rw.rw_users (rw_user_id, rw_email, rw_display_name, rw_role_id, rw_locale)
                VALUES (?, ?, ?, 2, 'en')
                """, id, prefix + "-" + id + "@riwi.co", prefix + " user");
        return UserId.of(id);
    }

    private void setPassword(UserId user, String email, String rawPassword) {
        JdbcTemplate migrator = PostgresTestSupport.migratorTemplate();
        migrator.update("UPDATE rw.rw_users SET rw_email = ? WHERE rw_user_id = ?", email, user.value());
        migrator.update("""
                INSERT INTO rw.rw_user_credentials (rw_user_id, rw_password_hash, rw_password_algorithm)
                VALUES (?, ?, 'argon2id')
                ON CONFLICT (rw_user_id) DO UPDATE SET rw_password_hash = EXCLUDED.rw_password_hash
                """, user.value(), passwordHasher.hash(rawPassword));
    }

    private void storeEmbedding(MessageId messageId, float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) literal.append(',');
            literal.append(vector[i]);
        }
        literal.append(']');
        PostgresTestSupport.migratorTemplate().update("""
                INSERT INTO rw.rw_message_embeddings
                    (rw_message_id, rw_embedding_model_id, rw_embedding, rw_source_revision, rw_content_hash)
                VALUES (?, 1, ?::vector, 1, sha256(convert_to(?::text, 'UTF8')))
                ON CONFLICT (rw_message_id) DO UPDATE SET rw_embedding = EXCLUDED.rw_embedding
                """, messageId.value(), literal.toString(), String.valueOf(messageId.value()));
    }
}
