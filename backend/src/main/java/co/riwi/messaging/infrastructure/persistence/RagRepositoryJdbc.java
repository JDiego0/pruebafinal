package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.model.RagCandidate;
import co.riwi.messaging.domain.port.RagRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Calls rw.rw_fn_rag_retrieve_candidates — SECURITY INVOKER, runs under
 * app.current_user_id so the vector search is already RLS-scoped (AI-08).
 */
public class RagRepositoryJdbc implements RagRepository {

    private final JdbcTemplate jdbc;

    public RagRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RagCandidate> retrieveCandidates(float[] queryVector, ChannelId channelId,
                                                   int limit, double similarityFloor) {
        String vectorLiteral = toVectorLiteral(queryVector);
        UUID channelUuid = channelId != null ? channelId.value() : null;

        return jdbc.query(
                "SELECT * FROM rw.rw_fn_rag_retrieve_candidates(?::vector, ?, ?, ?)",
                (rs, rowNum) -> mapRow(rs),
                vectorLiteral, channelUuid, limit, similarityFloor
        );
    }

    private RagCandidate mapRow(ResultSet rs) throws SQLException {
        return new RagCandidate(
                MessageId.of(rs.getLong("message_id")),
                new ChannelId(rs.getObject("channel_id", UUID.class)),
                rs.getString("body"),
                rs.getString("sender_display_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getDouble("similarity")
        );
    }

    private String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
