package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.RagCandidate;
import co.riwi.messaging.domain.port.RagRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Calls rw.rw_fn_rag_retrieve_candidates — SECURITY INVOKER, executed inside a
 * transaction that already carries app.current_user_id, so the vector search is
 * RLS-scoped before a single candidate leaves the database (AI-08, SEC-06).
 *
 * <p>The similarity floor is applied on the rows the function returns. It cannot
 * widen the result set: the function has already bounded it to the actor's
 * authorized messages, so this only ever discards further.
 */
public class RagRepositoryJdbc implements RagRepository {

    private static final String SQL = """
            SELECT c.rw_message_id,
                   c.rw_channel_id,
                   c.rw_body,
                   u.rw_display_name,
                   c.rw_created_at,
                   c.rw_similarity
            FROM rw.rw_fn_rag_retrieve_candidates(?::vector, ?, ?) c
            LEFT JOIN rw.rw_users u ON u.rw_user_id = c.rw_sender_id
            WHERE c.rw_similarity >= ?
            ORDER BY c.rw_similarity DESC
            """;

    private final JdbcTemplate jdbc;

    public RagRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RagCandidate> retrieveCandidates(float[] queryVector, ChannelId channelId,
                                                 int limit, double similarityFloor) {
        return jdbc.query(SQL, (rs, rowNum) -> mapRow(rs),
                toVectorLiteral(queryVector),
                channelId != null ? channelId.value() : null,
                limit,
                similarityFloor);
    }

    private RagCandidate mapRow(ResultSet rs) throws SQLException {
        return new RagCandidate(
                MessageId.of(rs.getLong("rw_message_id")),
                new ChannelId(rs.getObject("rw_channel_id", UUID.class)),
                rs.getString("rw_body"),
                rs.getString("rw_display_name"),
                rs.getTimestamp("rw_created_at").toInstant(),
                rs.getDouble("rw_similarity"));
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
