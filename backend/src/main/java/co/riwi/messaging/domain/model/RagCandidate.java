package co.riwi.messaging.domain.model;

import java.util.List;

/**
 * OBJ-06: one row returned by rw_fn_rag_retrieve_candidates.
 * The embedding is intentionally absent — callers only need the text
 * and the similarity score; the vector is stored but never leaves the DB.
 */
public record RagCandidate(
        MessageId messageId,
        ChannelId channelId,
        String body,
        String senderDisplayName,
        java.time.Instant createdAt,
        double similarity
) {}
