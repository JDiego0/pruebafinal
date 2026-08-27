package co.riwi.messaging.infrastructure.config;

import co.riwi.messaging.domain.port.AIProvider;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import co.riwi.messaging.infrastructure.ai.*;
import co.riwi.messaging.infrastructure.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI provider, embedding provider, embedding worker, and all AI repositories.
 * Separated from UseCaseConfig so AI infrastructure can be conditionally excluded in tests.
 */
@Configuration
@EnableScheduling
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    // ── Chat provider ─────────────────────────────────────────────────────────

    @Bean
    public AIProvider aiProvider(
            @Value("${app.ai.enabled:false}") boolean aiEnabled,
            @Value("${app.ai.base-url:}") String baseUrl,
            @Value("${app.ai.model:llama-3.3-70b-versatile}") String model,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds
    ) {
        if (!aiEnabled || apiKey == null || apiKey.isBlank()) {
            log.info("AI chat provider disabled (AI_ENABLED=false or no API key)");
            return new NullAIProvider();
        }
        log.info("AI chat provider: {} / model={}", baseUrl, model);
        return new OpenAiCompatibleChatProvider(baseUrl, model, apiKey, timeoutSeconds);
    }

    // ── Embedding provider ────────────────────────────────────────────────────

    @Bean
    public EmbeddingProvider embeddingProvider(
            @Value("${app.embedding.base-url:}") String baseUrl,
            @Value("${app.embedding.model:}") String model,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.embedding.dimensions:1536}") int dims
    ) {
        if (baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()) {
            log.info("Embedding provider: {} / model={}", baseUrl, model);
            return new OpenAiCompatibleEmbeddingProvider(baseUrl, model, apiKey, timeoutSeconds, dims);
        }
        log.info("Embedding provider: deterministic-local (offline, no credentials needed)");
        return new DeterministicLocalEmbeddingProvider();
    }

    // ── AI infrastructure repositories ────────────────────────────────────────

    @Bean
    public co.riwi.messaging.infrastructure.persistence.RagRepositoryJdbc ragRepositoryJdbc(
            JdbcTemplate jdbcTemplate) {
        return new RagRepositoryJdbc(jdbcTemplate);
    }

    @Bean
    public co.riwi.messaging.infrastructure.persistence.EmbeddingRepositoryJdbc embeddingRepositoryJdbc(
            JdbcTemplate jdbcTemplate) {
        return new EmbeddingRepositoryJdbc(jdbcTemplate);
    }

    @Bean
    public co.riwi.messaging.infrastructure.persistence.SystemPromptRepositoryJdbc systemPromptRepositoryJdbc(
            JdbcTemplate jdbcTemplate) {
        return new SystemPromptRepositoryJdbc(jdbcTemplate);
    }

    @Bean
    public co.riwi.messaging.infrastructure.persistence.CopilotRepositoryJdbc copilotRepositoryJdbc(
            JdbcTemplate jdbcTemplate) {
        return new CopilotRepositoryJdbc(jdbcTemplate);
    }

    // ── Embedding worker ──────────────────────────────────────────────────────
    // The embedding worker uses the app JdbcTemplate. It sets a special system
    // actor UUID that has BYPASSRLS effectively via the rw_migrator datasource.
    // In Docker, the worker shares the app connection and fetches bodies via
    // the rw_app role (messages visible to rw_app via SECURITY DEFINER select).
    @Bean
    public EmbeddingWorker embeddingWorker(
            EmbeddingProvider embeddingProvider,
            EmbeddingRepositoryJdbc embeddingRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${app.embedding.model:local-deterministic}") String modelCode
    ) {
        return new EmbeddingWorker(embeddingProvider, embeddingRepository, jdbcTemplate, modelCode);
    }
}
