package com.coursepick.config;

import com.coursepick.gemini.GeminiClient;
import com.coursepick.places.GooglePlacesClient;
import com.coursepick.places.PlaceLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public CoursepickSettings coursepickSettings(CoursepickProperties properties, ObjectMapper objectMapper)
            throws IOException {
        Path path = Path.of(properties.configPath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("설정 파일을 찾을 수 없습니다: " + path.toAbsolutePath());
        }
        return new CoursepickSettings(objectMapper.readTree(Files.readString(path)));
    }

    @Bean
    @Conditional(GooglePlacesKeyCondition.class)
    public PlaceLookupService placeLookupService(CoursepickProperties properties, CoursepickSettings settings)
            throws IOException {
        GooglePlacesClient client = new GooglePlacesClient(properties.keys().googlePlaces().strip());
        PlaceLookupService service = new PlaceLookupService(
                client,
                Path.of(properties.places().mappingsPath()),
                Path.of(properties.places().officialHoursPath()));
        var matching = settings.root().path("placeMatching");
        service.acceptSingleCandidate(matching.path("acceptSingleCandidate").asBoolean(false));
        if (matching.path("enabled").asBoolean(false)) {
            service.enableRuntimeMatching(
                    Path.of(matching.path("cachePath").asText("data/runtime-mappings.json")),
                    matching.path("dailySearchLimit").asInt(300));
        }
        return service;
    }

    @Bean
    @Conditional(GeminiKeyCondition.class)
    public GeminiClient geminiClient(CoursepickProperties properties, CoursepickSettings settings) {
        var gemini = settings.root().path("gemini");
        return new GeminiClient(
                properties.keys().gemini().strip(),
                gemini.path("model").asText("gemini-3.5-flash-lite"),
                gemini.path("timeoutSeconds").asInt(40));
    }

    @Bean
    public RestClient kakaoRestClient(RestClient.Builder builder, CoursepickProperties properties) {
        return builder
                .baseUrl(properties.endpoints().kakaoLocal())
                .defaultHeader("Authorization", "KakaoAK " + properties.keys().kakaoRest().strip())
                .build();
    }

    @Bean
    public RestClient googlePlacesClient(RestClient.Builder builder, CoursepickProperties properties) {
        return builder
                .baseUrl(properties.endpoints().googlePlaces())
                .defaultHeader("X-Goog-Api-Key", properties.keys().googlePlaces().strip())
                .build();
    }
}
