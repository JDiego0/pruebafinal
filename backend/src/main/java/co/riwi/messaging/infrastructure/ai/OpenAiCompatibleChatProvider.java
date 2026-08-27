package co.riwi.messaging.infrastructure.ai;

import co.riwi.messaging.domain.model.ChatRequest;
import co.riwi.messaging.domain.model.ChatResponse;
import co.riwi.messaging.domain.port.AIProvider;
import co.riwi.messaging.domain.error.ProviderUnavailable;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI-03: OpenAI-compatible chat completion adapter.
 * Works with Groq (AI_BASE_URL=https://api.groq.com/openai/v1),
 * OpenAI, Together, Ollama, LM Studio — pure configuration.
 * AI-09: retrieved messages are wrapped in delimiter-separated untrusted blocks
 *        so they cannot override the system prompt via injection.
 */
public class OpenAiCompatibleChatProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatProvider.class);
    /**
     * Lenient on unknown fields by design. "OpenAI-compatible" is a family of
     * dialects, not one schema: providers add `reasoning`, `refusal`,
     * `finish_reason`, vendor blocks, and each new model adds more. Failing to
     * deserialize a response we could otherwise read would turn a working answer
     * into PROVIDER_ERROR.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutSeconds;
    private final HttpClient http;

    public OpenAiCompatibleChatProvider(String baseUrl, String model,
                                         String apiKey, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest req) {
        try {
            String systemContent = buildSystemContent(req);
            String userContent = "Question: " + req.question();

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemContent),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "max_tokens", req.maxTokens(),
                    "temperature", 0.3
            );

            String bodyJson = MAPPER.writeValueAsString(body);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = http.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("AI provider returned HTTP {}: {}", response.statusCode(), response.body());
                throw new ProviderUnavailable("Chat provider returned HTTP " + response.statusCode());
            }

            CompletionResponse cr = MAPPER.readValue(response.body(), CompletionResponse.class);
            if (cr.choices() == null || cr.choices().isEmpty() || cr.choices().get(0).message() == null) {
                throw new ProviderUnavailable("Chat provider returned no choices");
            }
            String content = cr.choices().get(0).message().content();

            // Token counts are reporting, not correctness: a provider that omits
            // them must not cost the user their answer.
            Usage usage = cr.usage() != null ? cr.usage() : new Usage(0, 0, 0);

            return new ChatResponse(content,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens());

        } catch (ProviderUnavailable e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat provider error", e);
            throw new ProviderUnavailable("Chat provider error: " + e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * AI-09: untrusted context delimited so it cannot override system instructions.
     * Delimiter-neutralization: any occurrence of the delimiter inside a message
     * body is replaced with a visually similar Unicode homoglyph.
     */
    private String buildSystemContent(ChatRequest req) {
        String delimiter = "=== RETRIEVED CONTEXT ===";
        String endDelimiter = "=== END CONTEXT ===";

        StringBuilder sb = new StringBuilder();
        sb.append(req.systemPrompt()).append('\n').append('\n');
        sb.append("Actor: ").append(sanitize(req.identityBlock(), delimiter)).append('\n').append('\n');

        if (req.contextBlock() != null && !req.contextBlock().isBlank()) {
            sb.append(delimiter).append('\n');
            // AI-09: prevent injection by replacing the delimiter inside context
            sb.append(sanitize(req.contextBlock(), delimiter));
            sb.append('\n').append(endDelimiter).append('\n').append('\n');
            sb.append("Answer the question using only the retrieved context above. ");
            sb.append("Cite each message you use with [msg:<id>] inline. ");
            sb.append("If the context is insufficient, say so explicitly.");
        } else {
            sb.append("No relevant context was found for this question. ");
            sb.append("State that clearly without making up information.");
        }
        return sb.toString();
    }

    private String sanitize(String text, String delimiter) {
        if (text == null) return "";
        // Replace the exact delimiter string with a visually similar but structurally distinct version
        return text.replace(delimiter, delimiter.replace("=", "\u2550"));
    }

    // ── Wire format records ──────────────────────────────────────────────────

    record CompletionResponse(List<Choice> choices, Usage usage) {}
    record Choice(Message message) {}
    record Message(String content) {}
    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
