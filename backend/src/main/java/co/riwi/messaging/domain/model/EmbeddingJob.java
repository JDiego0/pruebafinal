package co.riwi.messaging.domain.model;

/**
 * One claimed row of the embedding outbox, carrying everything the worker needs
 * to compute and store a vector without a second read.
 *
 * @param revision the message revision the body was read at — stored alongside
 *                 the vector so a later edit is detectable as stale
 */
public record EmbeddingJob(
        long jobId,
        MessageId messageId,
        String body,
        int revision
) {}
