package co.riwi.messaging.domain.port;

import java.util.List;

/**
 * AI-02: strategy port for text embedding.
 * Configured independently from AIProvider (D-21: Groq has no embedding models).
 */
public interface EmbeddingProvider {
    /**
     * Embed one or more texts. Returns a parallel list of float[] vectors.
     * Each vector must have exactly EMBEDDING_DIMENSIONS (1536) elements.
     */
    List<float[]> embed(List<String> texts);

    /** Dimensions of the vectors produced by this provider. Must match the DB column. */
    int dimensions();

    boolean isEnabled();
}
