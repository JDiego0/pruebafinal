package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.EmbeddingJob;

import java.util.List;

/** AI-05, AI-06: drains the embedding outbox and persists vectors. */
public interface EmbeddingRepository {

    /**
     * Claims at most {@code batchSize} unprocessed jobs together with the body
     * to embed. Concurrent workers never claim the same row.
     */
    List<EmbeddingJob> claimPendingJobs(int batchSize);

    /** Stores the computed vector and marks the job processed, in one statement. */
    void storeEmbedding(EmbeddingJob job, float[] vector, short embeddingModelId);

    /** Records a failed attempt so the job can be retried and inspected. */
    void markJobFailed(long jobId, String error);

    /** Surrogate key of the active embedding model, or null when none is registered. */
    Short activeEmbeddingModelId(String modelCode);
}
