package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.EmbeddingJob;
import co.riwi.messaging.domain.model.MessageId;

import java.util.List;
import java.util.UUID;

/** AI-05, AI-06: manage the embedding job queue and persist vectors. */
public interface EmbeddingRepository {

    /** Claim at most {@code batchSize} pending jobs (marks them IN_PROGRESS atomically). */
    List<EmbeddingJob> claimPendingJobs(int batchSize);

    /**
     * Store the computed vector for a message.
     * Replaces any existing embedding for the same message.
     */
    void storeEmbedding(MessageId messageId, float[] vector, String modelCode, UUID jobId);

    /** Mark a job as FAILED with an error message. */
    void markJobFailed(UUID jobId, String error);
}
