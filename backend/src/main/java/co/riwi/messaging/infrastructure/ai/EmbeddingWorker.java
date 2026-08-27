package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.model.EmbeddingJob;
import co.riwi.messaging.domain.model.MessageId;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import co.riwi.messaging.domain.port.EmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI-05, AI-06: polls rw_embedding_jobs and computes vectors in the background.
 * Uses the migrator-role JdbcTemplate so it can bypass RLS to write vectors
 * (the embedding table is populated by a trusted system process, not end-user requests).
 *
 * The worker runs with Spring's @Scheduled thread pool — no user request context.
 */
public class EmbeddingWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingWorker.class);
    private static final int BATCH_SIZE = 32;

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingRepository embeddingRepository;
    private final JdbcTemplate migratorJdbc; // bypass-RLS for reading message bodies
    private final String modelCode;

    public EmbeddingWorker(EmbeddingProvider embeddingProvider,
                            EmbeddingRepository embeddingRepository,
                            JdbcTemplate migratorJdbc,
                            String modelCode) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingRepository = embeddingRepository;
        this.migratorJdbc = migratorJdbc;
        this.modelCode = modelCode;
    }

    @Scheduled(fixedDelayString = "${app.embedding.worker-delay-ms:5000}")
    @Transactional
    public void processJobs() {
        if (!embeddingProvider.isEnabled()) return;

        List<EmbeddingJob> jobs = embeddingRepository.claimPendingJobs(BATCH_SIZE);
        if (jobs.isEmpty()) return;

        log.debug("Embedding worker: processing {} jobs", jobs.size());

        // Fetch message bodies in bulk (bypasses RLS — migrator role)
        List<Long> ids = jobs.stream().map(j -> j.messageId().value()).toList();
        List<EmbeddingJob> jobsWithBody = fetchBodies(jobs, ids);

        List<String> texts = jobsWithBody.stream().map(EmbeddingJob::body).toList();
        List<float[]> vectors;
        try {
            vectors = embeddingProvider.embed(texts);
        } catch (Exception e) {
            log.warn("Embedding provider error for batch, marking jobs failed", e);
            for (EmbeddingJob job : jobsWithBody) {
                embeddingRepository.markJobFailed(job.jobId(), e.getMessage());
            }
            return;
        }

        for (int i = 0; i < jobsWithBody.size(); i++) {
            EmbeddingJob job = jobsWithBody.get(i);
            try {
                embeddingRepository.storeEmbedding(job.messageId(), vectors.get(i), modelCode, job.jobId());
            } catch (Exception e) {
                log.warn("Failed to store embedding for job {}: {}", job.jobId(), e.getMessage());
                embeddingRepository.markJobFailed(job.jobId(), e.getMessage());
            }
        }
    }

    private List<EmbeddingJob> fetchBodies(List<EmbeddingJob> jobs, List<Long> ids) {
        // Use SECURITY DEFINER function — rw_app can call it without a user RLS context.
        // The function runs as the definer (BYPASSRLS) but only returns non-deleted messages.
        Long[] idArray = ids.toArray(new Long[0]);

        var rows = migratorJdbc.queryForList(
                "SELECT message_id, body FROM rw.rw_fn_get_message_bodies_for_embedding(?::bigint[])",
                new Object[]{migratorJdbc.getDataSource() != null
                        ? "{" + ids.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse("") + "}"
                        : "{}"}
        );

        return jobs.stream().map(job -> {
            String body = rows.stream()
                    .filter(r -> ((Number) r.get("message_id")).longValue() == job.messageId().value())
                    .map(r -> (String) r.get("body"))
                    .findFirst()
                    .orElse("");
            return new EmbeddingJob(job.jobId(), job.messageId(), body);
        }).toList();
    }
}
