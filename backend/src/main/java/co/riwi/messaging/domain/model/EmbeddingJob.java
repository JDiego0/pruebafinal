package co.riwi.messaging.domain.model;

import java.util.UUID;

/** Represents one pending embedding job row. */
public record EmbeddingJob(
        UUID jobId,
        MessageId messageId,
        String body
) {}
