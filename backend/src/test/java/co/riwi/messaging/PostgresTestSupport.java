package co.riwi.messaging;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/**
 * Boots the real PostgreSQL the security suite runs against and applies the
 * production migrations to it — never an in-memory substitute (TEST-01).
 *
 * <p>By default a disposable {@code pgvector/pgvector:pg16} container is started
 * through Testcontainers. When {@code TEST_POSTGRES_URL} is present in the
 * environment the suite targets that server instead, which is what CI and the
 * documented {@code docker compose up -d db} workflow use when the Docker socket
 * is not reachable from the JVM.
 */
final class PostgresTestSupport {

    static final String MIGRATOR_PASSWORD = "mig_pass";
    static final String APP_PASSWORD = "app_pass";

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String bootstrapUser;
    private static String bootstrapPassword;
    private static boolean migrated;
    private static JdbcTemplate migratorTemplate;

    private PostgresTestSupport() {
    }

    static synchronized void start() {
        if (jdbcUrl != null) {
            return;
        }
        String external = System.getenv("TEST_POSTGRES_URL");
        if (external != null && !external.isBlank()) {
            jdbcUrl = external;
            bootstrapUser = envOrDefault("TEST_POSTGRES_USER", "postgres");
            bootstrapPassword = envOrDefault("TEST_POSTGRES_PASSWORD", "postgres");
        } else {
            container = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("bd_juandiego_garcia_nakamoto")
                    .withUsername("postgres")
                    .withPassword("postgres");
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            jdbcUrl = container.getJdbcUrl();
            bootstrapUser = container.getUsername();
            bootstrapPassword = container.getPassword();
        }
    }

    static synchronized void migrateAndSeedCatalogues() {
        start();
        if (migrated) {
            return;
        }
        Flyway.configure()
                .dataSource(jdbcUrl, bootstrapUser, bootstrapPassword)
                .locations("filesystem:../database/migrations")
                .cleanDisabled(false)
                .placeholders(Map.of(
                        "rwMigratorPassword", MIGRATOR_PASSWORD,
                        "rwAppPassword", APP_PASSWORD))
                .load()
                .migrate();
        seedCatalogues();
        migrated = true;
    }

    /**
     * The lookup tables the schema's foreign keys depend on. Production loads
     * them from {@code database/seeds/seed.json}; the suite inserts the same
     * rows directly so it stays independent of the seed file's contents.
     */
    private static void seedCatalogues() {
        JdbcTemplate migrator = migratorTemplate();
        migrator.update("""
                INSERT INTO rw.rw_roles (rw_role_id, rw_role_code, rw_role_name, rw_description)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'ADMIN', 'Administrator', 'Workspace administrator'),
                       (2, 'MEMBER', 'Member', 'Regular member')
                ON CONFLICT DO NOTHING
                """);
        migrator.update("""
                INSERT INTO rw.rw_channel_types (rw_channel_type_id, rw_channel_type_code, rw_description)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'PUBLIC', 'Discoverable by everyone'),
                       (2, 'PRIVATE', 'Members only'),
                       (3, 'DIRECT', 'Exactly two people')
                ON CONFLICT DO NOTHING
                """);
        migrator.update("""
                INSERT INTO rw.rw_channel_member_roles (rw_channel_member_role_id, rw_channel_member_role_code, rw_description)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'OWNER', 'Channel owner'),
                       (2, 'MODERATOR', 'Channel moderator'),
                       (3, 'MEMBER', 'Channel member')
                ON CONFLICT DO NOTHING
                """);
        migrator.update("""
                INSERT INTO rw.rw_ai_providers (rw_ai_provider_id, rw_provider_code, rw_display_name, rw_is_openai_compatible, rw_is_active)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'groq', 'Groq', true, true),
                       (2, 'local', 'Local deterministic', false, true)
                ON CONFLICT DO NOTHING
                """);
        migrator.update("""
                INSERT INTO rw.rw_embedding_models (rw_embedding_model_id, rw_ai_provider_id, rw_model_code, rw_dimensions, rw_is_active)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 2, 'local-deterministic-1536', 1536, true)
                ON CONFLICT DO NOTHING
                """);
        migrator.update("""
                INSERT INTO rw.rw_system_prompts (rw_system_prompt_id, rw_prompt_version, rw_prompt_body, rw_is_active)
                OVERRIDING SYSTEM VALUE
                VALUES (1, 'test-1.0.0', 'Answer only from the provided context.', true)
                ON CONFLICT DO NOTHING
                """);
    }

    static String jdbcUrl() {
        start();
        return jdbcUrl;
    }

    /**
     * A single shared template: rw_migrator is created with CONNECTION LIMIT 5,
     * so the suite must not open a fresh datasource per call.
     */
    static synchronized JdbcTemplate migratorTemplate() {
        if (migratorTemplate == null) {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl());
            ds.setUsername("rw_migrator");
            ds.setPassword(MIGRATOR_PASSWORD);
            ds.setMaximumPoolSize(2);
            ds.setPoolName("rw-migrator-test-pool");
            migratorTemplate = new JdbcTemplate(ds);
        }
        return migratorTemplate;
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
