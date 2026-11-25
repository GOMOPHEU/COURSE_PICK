package com.coursepick.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoLocalClient {

    private static final int PAGE_SIZE = 15;
    private static final int MAX_PAGES = 3;

    private final RestClient client;

    public KakaoLocalClient(@Qualifier("kakaoRestClient") RestClient client) {
        this.client = client;
    }

    public List<KakaoPlace> searchCategory(String categoryGroupCode, double lat, double lng, int radiusM) {
        return fetchPages(page -> client.get()
                .uri(uri -> uri.path("/v2/local/search/category.json")
                        .queryParam("category_group_code", categoryGroupCode)
                        .queryParam("x", lng)
                        .queryParam("y", lat)
                        .queryParam("radius", radiusM)
                        .queryParam("sort", "distance")
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(JsonNode.class));
    }

    public List<KakaoPlace> searchKeyword(String keyword, double lat, double lng, int radiusM) {
        return fetchPages(page -> client.get()
                .uri(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", keyword)
                        .queryParam("x", lng)
                        .queryParam("y", lat)
                        .queryParam("radius", radiusM)
                        .queryParam("sort", "distance")
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(JsonNode.class));
    }

    private List<KakaoPlace> fetchPages(java.util.function.IntFunction<JsonNode> fetcher) {
        List<KakaoPlace> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = fetcher.apply(page);
            if (body == null) {
                break;
            }
            for (JsonNode doc : body.path("documents")) {
                result.add(toPlace(doc));
            }
            if (body.path("meta").path("is_end").asBoolean(true)) {
                break;
            }
        }
        return result;
    }

    private KakaoPlace toPlace(JsonNode doc) {
        return new KakaoPlace(
                doc.path("id").asText(),
                doc.path("place_name").asText(),
                doc.path("category_name").asText(),
                doc.path("category_group_code").asText(),
                doc.path("address_name").asText(),
                doc.path("road_address_name").asText(),
                doc.path("y").asDouble(),
                doc.path("x").asDouble(),
                parseInt(doc.path("distance").asText("")),
                doc.path("place_url").asText());
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
