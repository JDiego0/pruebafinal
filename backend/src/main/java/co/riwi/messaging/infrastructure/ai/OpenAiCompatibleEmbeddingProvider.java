package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.error.ProviderUnavailable;
import co.riwi.messaging.domain.port.EmbeddingProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** OpenAI-compatible /v1/embeddings adapter (works with OpenAI, Ollama, Together, etc.) */
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BATCH_SIZE = 32;

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutSeconds;
    private final int dims;
    private final HttpClient http;

    public OpenAiCompatibleEmbeddingProvider(String baseUrl, String model, String apiKey,
                                              int timeoutSeconds, int dims) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.dims = dims;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            all.addAll(embedBatch(batch));
        }
        return all;
    }

    private List<float[]> embedBatch(List<String> batch) {
        try {
            String bodyJson = MAPPER.writeValueAsString(Map.of("model", model, "input", batch));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ProviderUnavailable("Embedding provider returned HTTP " + res.statusCode());
            }
            EmbeddingResponse er = MAPPER.readValue(res.body(), EmbeddingResponse.class);
            return er.data().stream().map(d -> toFloatArray(d.embedding())).toList();
        } catch (ProviderUnavailable e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding provider error", e);
            throw new ProviderUnavailable("Embedding provider error: " + e.getMessage());
        }
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] arr = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) arr[i] = doubles.get(i).floatValue();
        return arr;
    }

    @Override
    public int dimensions() { return dims; }

    @Override
    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    record EmbeddingResponse(List<EmbeddingData> data) {}
    record EmbeddingData(@JsonProperty("embedding") List<Double> embedding) {}
}
