package com.coursepick.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 4000;

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public GeminiClient(String apiKey, String model, int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("\n") || apiKey.contains("\r")) {
            throw new IllegalArgumentException("GEMINI_API_KEY is required");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String generate(String prompt, JsonNode responseSchema) {
        ObjectNode body = json.createObjectNode();
        body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", responseSchema);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = null;
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 503 || attempt == MAX_ATTEMPTS - 1) {
                    break;
                }
                Thread.sleep(RETRY_DELAY_MS);
            }
            if (response.statusCode() != 200) {
                String message = json.readTree(response.body()).path("error").path("message").asText("");
                throw new GeminiException("Gemini 응답 오류 " + response.statusCode() + " " + message);
            }
            String text = json.readTree(response.body())
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new GeminiException("Gemini 응답이 비어 있습니다");
            }
            return text;
        } catch (IOException e) {
            throw new GeminiException("Gemini 호출 실패: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Gemini 호출이 중단되었습니다", e);
        }
    }
}
