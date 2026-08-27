package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.UserId;
import co.riwi.messaging.domain.port.CopilotRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** JDBC adapter for copilot turns, citations and usage ledger. */
public class CopilotRepositoryJdbc implements CopilotRepository {

    private final JdbcTemplate jdbc;

    public CopilotRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TurnResult recordTurn(UserId userId, UUID threadId, String question,
                                  String status, String answer, String promptVersion,
                                  int promptTokens, int completionTokens,
                                  String providerCode, String modelCode) {
        UUID turnId = UUID.randomUUID();
        UUID actualThread = threadId != null ? threadId : UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO rw.rw_copilot_turns
                    (turn_id, thread_id, user_id, question, status, answer, prompt_version,
                     prompt_tokens, completion_tokens, total_tokens, provider_code, model_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                turnId, actualThread, userId.value(), question, status, answer,
                promptVersion, promptTokens, completionTokens,
                promptTokens + completionTokens, providerCode, modelCode
        );

        return new TurnResult(turnId, actualThread);
    }

    @Override
    public void recordCitations(UUID turnId, List<MessageId> citedMessageIds) {
        for (int rank = 0; rank < citedMessageIds.size(); rank++) {
            jdbc.update(
                    "INSERT INTO rw.rw_copilot_citations (turn_id, message_id, rank) VALUES (?, ?, ?)",
                    turnId, citedMessageIds.get(rank).value(), rank + 1
            );
        }
    }

    @Override
    public UsageSummary usageSummary(UserId userId) {
        return jdbc.queryForObject(
                """
                SELECT
                    user_id::text,
                    COUNT(*) AS total_requests,
                    COALESCE(SUM(prompt_tokens), 0) AS total_prompt_tokens,
                    COALESCE(SUM(completion_tokens), 0) AS total_completion_tokens,
                    COALESCE(SUM(total_tokens), 0) AS total_tokens,
                    MIN(created_at) AS first_used_at,
                    MAX(created_at) AS last_used_at
                FROM rw.rw_copilot_turns
                WHERE user_id = ?
                GROUP BY user_id
                """,
                (rs, rowNum) -> new UsageSummary(
                        rs.getString("user_id"),
                        rs.getLong("total_requests"),
                        rs.getLong("total_prompt_tokens"),
                        rs.getLong("total_completion_tokens"),
                        rs.getLong("total_tokens"),
                        rs.getTimestamp("first_used_at") != null
                                ? rs.getTimestamp("first_used_at").toInstant() : null,
                        rs.getTimestamp("last_used_at") != null
                                ? rs.getTimestamp("last_used_at").toInstant() : null
                ),
                userId.value()
        );
    }
}
