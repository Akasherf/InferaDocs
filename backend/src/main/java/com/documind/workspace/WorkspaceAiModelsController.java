package com.documind.workspace;

import com.documind.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/ai")
public class WorkspaceAiModelsController {
    private final WorkspacePreferencesService preferences;
    private final JsonMapper json;
    private final URI tagsEndpoint;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public WorkspaceAiModelsController(WorkspacePreferencesService preferences, JsonMapper json,
           @Value("${app.rag.url:http://localhost:11434/api/generate}") String url) {
        this.preferences = preferences;
        this.json = json;
        // Reads the server-controlled Ollama endpoint; users cannot supply a URL.
        URI configured = URI.create(url);
        this.tagsEndpoint = configured.resolve("/api/tags");
    }

    public record AvailableModels(List<String> models, boolean available) {}
    @GetMapping("/models")
    public ApiResponse<AvailableModels> models(@PathVariable("organizationId") UUID org, HttpServletRequest req) {
        preferences.authorize(org,(UUID)req.getAttribute("userId"),false);
        try {
            HttpRequest query = HttpRequest.newBuilder(tagsEndpoint).GET()
               .timeout(Duration.ofSeconds(5)).build();
            var result = client.send(query,HttpResponse.BodyHandlers.ofString());
            if(result.statusCode()!=200) return ApiResponse.success(new AvailableModels(List.of(),false));
            JsonNode models = json.readTree(result.body()).path("models");
            List<String> names = new ArrayList<>();
            if(models.isArray()) for(JsonNode m:models) {
                JsonNode n=m.path("name");
                if(n.isTextual()) names.add(n.asText());
            }
            return ApiResponse.success(new AvailableModels(names,true));
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResponse.success(new AvailableModels(List.of(),false));
        } catch(Exception ignored) {
            return ApiResponse.success(new AvailableModels(List.of(),false));
        }
    }
}
