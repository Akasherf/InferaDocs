package com.documind.search;

import com.documind.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {
    public static final int DIMENSIONS = 768;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;
    private final String model;

    public EmbeddingService(@Value("${app.embeddings.url:http://localhost:11434/api/embed}") String endpoint,
                            @Value("${app.embeddings.model:nomic-embed-text}") String model) {
        this.endpoint = URI.create(endpoint);
        this.model = model;
    }

    public String model() { return model; }

    public float[] embed(String text) {
        try {
            String body = json.writeValueAsString(Map.of("model", model, "input", text));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            JsonNode vectors = json.readTree(response.body()).path("embeddings");
            if (!vectors.isArray() || vectors.size() != 1 || vectors.get(0).size() != DIMENSIONS)
                throw unavailable();
            float[] output = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                double value = vectors.get(0).get(i).asDouble(Double.NaN);
                if (!Double.isFinite(value)) throw unavailable();
                output[i] = (float) value;
            }
            return output;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE",
                "Embedding service unavailable or wrong dimensions. Start Ollama and pull " + model + ".");
    }

    public static String vectorLiteral(float[] values) {
        List<String> parts = new ArrayList<>(values.length);
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite embedding");
            parts.add(Float.toString(value));
        }
        return "[" + String.join(",", parts) + "]";
    }
}
