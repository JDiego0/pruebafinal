package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.port.EmbeddingProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * D-21, D-06: deterministic local embedding provider.
 * Produces stable 1536-dimensional vectors from a SHA-256 hash —
 * semantically meaningless but structurally valid. Allows the full
 * stack to run without credentials and all security tests to pass
 * with meaningful isolation properties (TEST-04 works because the
 * vectors are still deterministic per text).
 *
 * Never used in production when EMBEDDING_BASE_URL is configured.
 */
public class DeterministicLocalEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMS = 1536;

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(deterministicVector(text));
        }
        return result;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public boolean isEnabled() {
        return true; // always available — no credentials required
    }

    private float[] deterministicVector(String text) {
        float[] vec = new float[DIMS];
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] seed = sha.digest(text.getBytes(StandardCharsets.UTF_8));
            // Fill DIMS floats from repeating SHA rounds
            int idx = 0;
            byte[] block = seed;
            while (idx < DIMS) {
                block = sha.digest(block);
                for (int b = 0; b < block.length && idx < DIMS; b++, idx++) {
                    vec[idx] = (block[b] & 0xFF) / 255.0f - 0.5f;
                }
            }
            // L2-normalize
            double norm = 0;
            for (float v : vec) norm += (double) v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < DIMS; i++) vec[i] /= (float) norm;
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        return vec;
    }
}
