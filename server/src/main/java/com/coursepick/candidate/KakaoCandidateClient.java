package com.coursepick.candidate;

import com.coursepick.config.CoursepickProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class KakaoCandidateClient {
    private final RestClient client;
    private final CoursepickProperties properties;
    public KakaoCandidateClient(@Qualifier("kakaoLocalClient") RestClient client, CoursepickProperties properties) {
        this.client = client;
        this.properties = properties;
    }
    public record Page(List<KakaoPlace> places, boolean end) {}
    public Page search(CandidatePolicy.Search search, double lat, double lng, int radius, int page) {
        if (!properties.keys().hasKakaoRest()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_REST_KEY가 설정되지 않았습니다");
        try {
            JsonNode response = client.get().uri(builder -> {
                builder.path("/v2/local/search/" + (search.keyword() == null ? "category" : "keyword") + ".json")
                        .queryParam("x", lng).queryParam("y", lat).queryParam("radius", radius)
                        .queryParam("sort", "distance").queryParam("size", 15).queryParam("page", page);
                if (search.keyword() == null) builder.queryParam("category_group_code", search.groupCode());
                else builder.queryParam("query", search.keyword());
                return builder.build();
            }).retrieve().body(JsonNode.class);
            if (response == null || !response.path("documents").isArray() || !response.path("meta").path("is_end").isBoolean())
                throw new IllegalArgumentException("Malformed Kakao response");
            List<KakaoPlace> places = new ArrayList<>();
            for (JsonNode row : response.path("documents")) places.add(KakaoPlace.from(row));
            return new Page(List.copyOf(places), response.path("meta").path("is_end").asBoolean());
        } catch (RestClientException | IllegalArgumentException ex) {
            // Do not return provider response bodies (or treat outages as an empty candidate set).
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카카오 후보 조회에 실패했습니다");
        }
    }
}
