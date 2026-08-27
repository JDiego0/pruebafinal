package co.riwi.messaging.domain.port;

import co.riwi.messaging.domain.model.ChannelId;
import co.riwi.messaging.domain.model.RagCandidate;

import java.util.List;

/**
 * OBJ-06, AI-07, AI-08.
 * Retrieves authorized message candidates for the RAG pipeline.
 *
 * CRITICAL SECURITY NOTE: the underlying function rw_fn_rag_retrieve_candidates
 * is SECURITY INVOKER and runs inside a transaction carrying app.current_user_id.
 * No global ANN search is performed — the vector operator runs exclusively inside
 * the authorized set (AI-08, SEC-06). This port must never be called outside
 * a RlsTransactionRunner transaction.
 */
public interface RagRepository {

    /**
     * Retrieve at most {@code limit} messages semantically close to
     * {@code queryVector} that the current RLS actor is authorized to read.
     *
     * @param queryVector  embedding of the user's question (1536 floats)
     * @param channelId    optional scope to a single channel; null = all authorized channels
     * @param limit        maximum candidates (configured by RAG_CANDIDATE_LIMIT)
     * @param similarityFloor minimum cosine similarity (configured by RAG_SIMILARITY_FLOOR)
     */
    List<RagCandidate> retrieveCandidates(float[] queryVector, ChannelId channelId,
                                           int limit, double similarityFloor);
}
