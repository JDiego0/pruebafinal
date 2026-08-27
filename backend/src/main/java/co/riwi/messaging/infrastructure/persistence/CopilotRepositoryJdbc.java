package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.CopilotRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JDBC adapter for copilot threads, turns, citations and the usage ledger.
 * Every statement here runs as rw_app inside the actor's RLS transaction: the
 * WITH CHECK policies refuse a row whose owner is not the current actor, so a
 * turn can never be attributed to somebody else.
 */
public class CopilotRepositoryJdbc implements CopilotRepository {

    private final JdbcTemplate jdbc;

    public CopilotRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TurnResult recordTurn(UserId userId, UUID threadId, ChannelId channelId,
                                 short systemPromptId, String question, String answerStatus,
                                 String answer, int candidateCount) {
        UUID thread = threadId != null ? threadId : UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rw.rw_copilot_threads (rw_copilot_thread_id, rw_user_id, rw_channel_id)
                VALUES (?, ?, ?)
                ON CONFLICT (rw_copilot_thread_id) DO NOTHING
                """, thread, userId.value(), channelId != null ? channelId.value() : null);

        UUID turnId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rw.rw_copilot_turns
                    (rw_copilot_turn_id, rw_copilot_thread_id, rw_system_prompt_id,
                     rw_question, rw_answer, rw_answer_status, rw_candidate_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, turnId, thread, systemPromptId, question, answer, answerStatus, candidateCount);

        return new TurnResult(turnId, thread);
    }

    @Override
    public void recordCitations(UUID turnId, List<CitationRow> citations) {
        for (CitationRow citation : citations) {
            jdbc.update("""
                    INSERT INTO rw.rw_copilot_citations
                        (rw_copilot_turn_id, rw_message_id, rw_similarity, rw_rank)
                    VALUES (?, ?, ?, ?)
                    """, turnId, citation.messageId().value(), citation.similarity(), citation.rank());
        }
    }

    @Override
    public void recordUsage(UserId userId, UUID turnId, String providerCode, String modelCode,
                            String operation, int promptTokens, int completionTokens, int latencyMs) {
        Short providerId = providerId(providerCode);
        if (providerId == null) {
            // Unknown provider code (for example a turn that never reached a
            // provider): there is nothing meaningful to bill, so nothing is written.
            return;
        }
        jdbc.update("""
                INSERT INTO rw.rw_copilot_usage
                    (rw_user_id, rw_copilot_turn_id, rw_ai_provider_id, rw_model_code, rw_operation,
                     rw_prompt_tokens, rw_completion_tokens, rw_total_tokens, rw_latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId.value(), turnId, providerId, modelCode, operation,
                promptTokens, completionTokens, promptTokens + completionTokens, latencyMs);
    }

    private Short providerId(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        List<Short> ids = jdbc.queryForList(
                "SELECT rw_ai_provider_id FROM rw.rw_ai_providers WHERE rw_provider_code = ?",
                Short.class, providerCode);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public UsageSummary usageSummary(UserId userId) {
        try {
            return jdbc.queryForObject("""
                    SELECT count(*)                                  AS requests,
                           coalesce(sum(rw_prompt_tokens), 0)        AS prompt_tokens,
                           coalesce(sum(rw_completion_tokens), 0)    AS completion_tokens,
                           coalesce(sum(rw_total_tokens), 0)         AS total_tokens,
                           min(rw_created_at)                        AS first_used_at,
                           max(rw_created_at)                        AS last_used_at
                    FROM rw.rw_copilot_usage
                    """,
                    (rs, rowNum) -> {
                        Timestamp first = rs.getTimestamp("first_used_at");
                        Timestamp last = rs.getTimestamp("last_used_at");
                        return new UsageSummary(
                                userId.value().toString(),
                                rs.getLong("requests"),
                                rs.getLong("prompt_tokens"),
                                rs.getLong("completion_tokens"),
                                rs.getLong("total_tokens"),
                                first != null ? first.toInstant() : null,
                                last != null ? last.toInstant() : null);
                    });
        } catch (EmptyResultDataAccessException e) {
            return new UsageSummary(userId.value().toString(), 0, 0, 0, 0, null, null);
        }
    }
}
