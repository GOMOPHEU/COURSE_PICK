package com.coursepick.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

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
    public RestClient kakaoLocalClient(RestClient.Builder builder, CoursepickProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(5));
        return builder
                .requestFactory(requests)
                .baseUrl(properties.endpoints().kakaoLocal())
                .defaultHeader("Authorization", "KakaoAK " + properties.keys().kakaoRest())
                .build();
    }

    @Bean
    public RestClient googlePlacesClient(RestClient.Builder builder, CoursepickProperties properties) {
        return builder
                .baseUrl(properties.endpoints().googlePlaces())
                .defaultHeader("X-Goog-Api-Key", properties.keys().googlePlaces())
                .build();
    }
}
