package co.riwi.messaging.infrastructure.persistence;

import co.riwi.messaging.domain.model.EmbeddingJob;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.port.EmbeddingRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Drives the embedding outbox through the SECURITY DEFINER functions in V4/V16.
 * The connection is still rw_app: the worker never gains a broader identity than
 * the request path, it just reaches a narrow set of trusted functions that the
 * migrations granted it (AI-05, AI-06). Those functions are the reason the
 * worker can run with no actor bound at all.
 */
public class EmbeddingRepositoryJdbc implements EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmbeddingJob> claimPendingJobs(int batchSize) {
        return jdbc.query(
                "SELECT * FROM rw.rw_fn_claim_pending_embedding_jobs(?)",
                (rs, rowNum) -> new EmbeddingJob(
                        rs.getLong("rw_embedding_job_id"),
                        MessageId.of(rs.getLong("rw_message_id")),
                        rs.getString("rw_body"),
                        rs.getInt("rw_revision")),
                batchSize);
    }

    @Override
    public void storeEmbedding(EmbeddingJob job, float[] vector, short embeddingModelId) {
        // These functions return void, so they are called as queries whose one
        // row is discarded — JdbcTemplate.update() rejects a statement that
        // produces a result set at all.
        jdbc.query(
                "SELECT rw.rw_fn_store_message_embedding(?, ?, ?, ?::vector, ?, sha256(convert_to(?::text, 'UTF8')))",
                rs -> null,
                job.jobId(),
                job.messageId().value(),
                embeddingModelId,
                RagRepositoryJdbc.toVectorLiteral(vector),
                job.revision(),
                job.body());
    }

    @Override
    public void markJobFailed(long jobId, String error) {
        jdbc.query("SELECT rw.rw_fn_mark_embedding_job_failed(?, ?)", rs -> null, jobId, error);
    }

    @Override
    public Short activeEmbeddingModelId(String modelCode) {
        return jdbc.queryForObject(
                "SELECT rw.rw_fn_active_embedding_model_id(?)", Short.class, modelCode);
    }
}
