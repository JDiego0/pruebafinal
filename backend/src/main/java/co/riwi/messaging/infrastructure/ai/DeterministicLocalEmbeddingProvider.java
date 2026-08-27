package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.port.EmbeddingProvider;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * D-21, D-06: the offline embedding provider, used whenever
 * {@code EMBEDDING_BASE_URL} is empty.
 *
 * <p>This is <strong>feature hashing</strong> (the "hashing trick"): the text is
 * folded to lowercase ASCII, tokenized, and each token is hashed into one of
 * 1536 buckets with a signed weight, then the vector is L2-normalized. Cosine
 * similarity between two such vectors is therefore a measure of shared
 * vocabulary — crude next to a trained model, but a real signal, which is what
 * the RAG pipeline needs in order to retrieve anything at all.
 *
 * <p>An earlier version hashed the whole string in one go. That is stable and
 * structurally valid, but two texts that differ by a single character land in
 * unrelated directions, so a question was never similar to any message and
 * retrieval returned nothing for every query. Determinism alone is not enough:
 * the vector has to carry something about the text.
 *
 * <p>Deterministic across processes and JVM versions — the hash is FNV-1a over
 * UTF-8 bytes, not {@code Object.hashCode()}.
 *
 * <p>Changing this implementation invalidates every stored vector. See
 * README §10 for the re-embedding procedure.
 */
public class DeterministicLocalEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMS = 1536;

    /**
     * Words too common to say anything about a message. Both languages of the
     * corpus, kept deliberately short: this is a relevance nicety, not a
     * linguistic model.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            // Spanish
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "que", "en",
            "por", "para", "con", "sin", "del", "al", "se", "su", "sus", "lo", "es", "son", "fue",
            "ser", "esta", "este", "esto", "estos", "estas", "como", "mas", "pero", "ya", "no",
            "si", "me", "te", "nos", "le", "les", "a", "ha", "han", "hay", "muy", "the", "todo",
            // English
            "of", "and", "or", "to", "in", "on", "at", "for", "with", "without", "is", "are",
            "was", "were", "be", "been", "this", "that", "these", "those", "it", "its", "as",
            "an", "by", "from", "we", "you", "they", "i", "do", "does", "did", "has", "have",
            "had", "will", "would", "can", "could", "there", "here", "what", "when", "where",
            "which", "who", "how", "not", "but", "all", "any", "so", "if", "about"
    );

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(vectorize(text));
        }
        return vectors;
    }

    @Override
    public int dimensions() {
        return DIMS;
    }

    @Override
    public boolean isEnabled() {
        return true; // always available — no credentials required
    }

    // ── internals ────────────────────────────────────────────────────────────

    private float[] vectorize(String text) {
        float[] vector = new float[DIMS];
        if (text == null || text.isBlank()) {
            return vector;
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokenize(text)) {
            counts.merge(token, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            long hash = fnv1a(entry.getKey());
            int bucket = (int) Math.floorMod(hash, DIMS);
            // A second bit of the same hash decides the sign, so collisions
            // cancel out on average instead of always reinforcing each other.
            float sign = ((hash >>> 40) & 1L) == 0L ? 1f : -1f;
            // Sublinear term frequency: a word repeated ten times is more
            // important than one used once, but not ten times more.
            float weight = (float) (1.0 + Math.log(entry.getValue()));
            vector[bucket] += sign * weight;
        }

        return l2Normalize(vector);
    }

    /** Lowercases, strips accents, and splits on anything that is not a letter or digit. */
    private List<String> tokenize(String text) {
        String folded = Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        List<String> tokens = new ArrayList<>();
        for (String candidate : folded.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (candidate.length() >= 2 && !STOP_WORDS.contains(candidate)) {
                tokens.add(candidate);
            }
        }
        return tokens;
    }

    private float[] l2Normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float v : vector) {
            sumOfSquares += (double) v * v;
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) norm;
            }
        }
        return vector;
    }

    /** FNV-1a, 64-bit. Specified byte-for-byte, so the vectors are reproducible anywhere. */
    private static long fnv1a(String token) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : token.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
