package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.model.EmbeddingJob;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import co.riwi.messaging.domain.port.EmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI-05, AI-06: drains rw_embedding_jobs and computes vectors in the background.
 *
 * <p>The worker holds no user context — it runs on Spring's scheduler, outside
 * any request. It reaches message bodies only through the SECURITY DEFINER
 * outbox functions, which is what lets it vectorize content it is not itself
 * authorized to read. Nothing it writes is ever returned to a user directly:
 * reads always go back through the RLS-scoped retrieval path.
 */
public class EmbeddingWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingRepository embeddingRepository;
    private final String modelCode;
    private final int batchSize;

    public EmbeddingWorker(EmbeddingProvider embeddingProvider,
                           EmbeddingRepository embeddingRepository,
                           String modelCode,
                           int batchSize) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingRepository = embeddingRepository;
        this.modelCode = modelCode;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.embedding.worker-delay-ms:5000}")
    @Transactional
    public void processJobs() {
        if (!embeddingProvider.isEnabled()) {
            return;
        }

        List<EmbeddingJob> jobs = embeddingRepository.claimPendingJobs(batchSize);
        if (jobs.isEmpty()) {
            return;
        }

        Short modelId = embeddingRepository.activeEmbeddingModelId(modelCode);
        if (modelId == null) {
            log.warn("No active embedding model registered; {} job(s) left pending", jobs.size());
            return;
        }

        List<float[]> vectors;
        try {
            vectors = embeddingProvider.embed(jobs.stream().map(EmbeddingJob::body).toList());
        } catch (Exception e) {
            log.warn("Embedding provider failed for a batch of {}: {}", jobs.size(), e.getMessage());
            jobs.forEach(job -> embeddingRepository.markJobFailed(job.jobId(), e.getMessage()));
            return;
        }

        int stored = 0;
        for (int i = 0; i < jobs.size(); i++) {
            EmbeddingJob job = jobs.get(i);
            try {
                embeddingRepository.storeEmbedding(job, vectors.get(i), modelId);
                stored++;
            } catch (Exception e) {
                log.warn("Could not store embedding for job {}: {}", job.jobId(), e.getMessage());
                embeddingRepository.markJobFailed(job.jobId(), e.getMessage());
            }
        }
        log.debug("Embedding worker stored {}/{} vector(s)", stored, jobs.size());
    }
}
