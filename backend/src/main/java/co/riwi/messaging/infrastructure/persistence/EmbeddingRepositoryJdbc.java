package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.EmbeddingJob;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.port.EmbeddingRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/** JDBC adapter for the rw_embedding_jobs and rw_message_embeddings tables. */
public class EmbeddingRepositoryJdbc implements EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmbeddingJob> claimPendingJobs(int batchSize) {
        return jdbc.query(
                """
                UPDATE rw.rw_embedding_jobs
                SET status = 'IN_PROGRESS', started_at = NOW()
                WHERE job_id IN (
                    SELECT job_id FROM rw.rw_embedding_jobs
                    WHERE status = 'PENDING'
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING job_id, message_id
                """,
                (rs, rowNum) -> {
                    UUID jobId = rs.getObject("job_id", UUID.class);
                    long messageId = rs.getLong("message_id");
                    // body fetched in batch after claim
                    return new EmbeddingJob(jobId, MessageId.of(messageId), null);
                },
                batchSize
        );
    }

    @Override
    public void storeEmbedding(MessageId messageId, float[] vector, String modelCode, UUID jobId) {
        String vectorLiteral = toVectorLiteral(vector);
        jdbc.update(
                """
                INSERT INTO rw.rw_message_embeddings (message_id, model_code, embedding)
                VALUES (?, ?, ?::vector)
                ON CONFLICT (message_id, model_code) DO UPDATE
                    SET embedding = EXCLUDED.embedding, updated_at = NOW()
                """,
                messageId.value(), modelCode, vectorLiteral
        );
        jdbc.update(
                """
                UPDATE rw.rw_embedding_jobs
                SET status = 'DONE', completed_at = NOW()
                WHERE job_id = ?
                """,
                jobId
        );
    }

    @Override
    public void markJobFailed(UUID jobId, String error) {
        jdbc.update(
                """
                UPDATE rw.rw_embedding_jobs
                SET status = 'FAILED', error_message = ?, completed_at = NOW()
                WHERE job_id = ?
                """,
                error, jobId
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
