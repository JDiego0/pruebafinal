package co.riwi.messaging.infrastructure.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * DB-20/OPS-03: loads database/seeds/seed.json's {@code data} block. Runs
 * ONLY under the {@code seed} Spring profile, against the rw_migrator
 * datasource (BYPASSRLS) — the running application never has this
 * capability (D-07). Table order matches FK dependency order, exactly as
 * documented in seed.schema.json. Idempotent: every insert uses
 * {@code ON CONFLICT DO NOTHING}, so re-running the seed loader against an
 * already-seeded database is a safe no-op.
 */
@Component
@Profile("seed")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    /** Tables whose PK is a small identity column the seed pins to a fixed value. */
    private static final Set<String> OVERRIDING_IDENTITY_TABLES = Set.of(
            "rw_roles", "rw_channel_types", "rw_channel_member_roles",
            "rw_ai_providers", "rw_embedding_models", "rw_system_prompts"
    );

    private static final List<String> TABLE_ORDER = List.of(
            "rw_roles", "rw_channel_types", "rw_channel_member_roles",
            "rw_ai_providers", "rw_embedding_models", "rw_system_prompts",
            "rw_users", "rw_user_credentials",
            "rw_channels", "rw_channel_members", "rw_direct_channel_pairs",
            "rw_messages"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final String seedFilePath;

    // @Qualifier is required here, not cosmetic: rwAppDataSource is @Primary
    // (DataSourceConfig), and Spring's autowiring resolves an unqualified
    // multi-candidate injection by @Primary BEFORE it ever considers
    // matching by parameter name — a bare `DataSource rwMigratorDataSource`
    // parameter silently receives the rw_app datasource instead. Caught only
    // by running the seed loader against a live database (it fails loudly
    // with "permission denied", since rw_app has no INSERT grant on lookup
    // tables) — exactly the kind of defect a code read would not catch.
    public SeedRunner(@Qualifier("rwMigratorDataSource") DataSource rwMigratorDataSource,
                       @Value("${app.seed.file}") String seedFilePath) {
        this.jdbc = new NamedParameterJdbcTemplate(rwMigratorDataSource);
        this.seedFilePath = seedFilePath;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File file = new File(seedFilePath);
        if (!file.exists()) {
            throw new IllegalStateException("Seed file not found at " + file.getAbsolutePath()
                    + " — set app.seed.file to database/seeds/seed.json");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        JsonNode data = root.get("data");
        if (data == null) {
            throw new IllegalStateException("seed.json has no top-level 'data' object");
        }

        log.info("Seeding database from {}", file.getAbsolutePath());
        for (String table : TABLE_ORDER) {
            JsonNode rows = data.get(table);
            if (rows == null || !rows.isArray()) {
                continue;
            }
            int inserted = insertRows(table, rows);
            log.info("  {} — {} row(s) attempted", table, inserted);
        }
        log.info("Seeding complete.");
    }

    private int insertRows(String table, JsonNode rows) {
        int count = 0;
        for (JsonNode row : rows) {
            insertRow(table, row);
            count++;
        }
        return count;
    }

    private void insertRow(String table, JsonNode row) {
        List<String> columns = new ArrayList<>();
        row.fieldNames().forEachRemaining(columns::add);

        StringBuilder sql = new StringBuilder("INSERT INTO rw.").append(table).append(" (");
        sql.append(String.join(", ", columns)).append(") ");
        if (OVERRIDING_IDENTITY_TABLES.contains(table)) {
            sql.append("OVERRIDING SYSTEM VALUE ");
        }
        sql.append("VALUES (");
        sql.append(columns.stream().map(c -> ":" + c).reduce((a, b) -> a + ", " + b).orElse(""));
        sql.append(") ON CONFLICT DO NOTHING");

        MapSqlParameterSource params = new MapSqlParameterSource();
        for (String column : columns) {
            params.addValue(column, toJavaValue(row.get(column)));
        }
        jdbc.update(sql.toString(), params);
    }

    private static final Pattern UUID_SHAPE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Object toJavaValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isInt()) {
            return value.intValue();
        }
        if (value.isLong()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            String text = value.textValue();
            // ISO-8601 timestamps in seed.json map straight to timestamptz.
            if (text.matches("\\d{4}-\\d{2}-\\d{2}T.*Z")) {
                return OffsetDateTime.parse(text);
            }
            // A JDBC bind parameter carrying a plain String is sent to
            // PostgreSQL as an explicitly-typed varchar and does NOT receive
            // the same implicit assignment-cast to uuid that a literal in
            // raw SQL text gets — binding java.util.UUID directly is what
            // the driver natively maps to the uuid column type. Found by
            // running the seed loader against a live database; a string
            // literal in ad-hoc psql testing had masked this earlier.
            if (UUID_SHAPE.matcher(text).matches()) {
                return UUID.fromString(text);
            }
            return text;
        }
        return value.toString();
    }
}
