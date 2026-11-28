package com.coursepick.candidate;

import com.fasterxml.jackson.databind.JsonNode;

public record KakaoPlace(String id, String name, String category, String groupCode,
                         double lat, double lng, String address, String url) {
    static KakaoPlace from(JsonNode row) {
        double lat = Double.parseDouble(row.path("y").asText());
        double lng = Double.parseDouble(row.path("x").asText());
        String id = row.path("id").asText();
        if (id.isBlank() || row.path("place_name").asText().isBlank()
                || !Double.isFinite(lat) || !Double.isFinite(lng)
                || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw new IllegalArgumentException("Invalid Kakao place");
        }
        return new KakaoPlace(id, row.path("place_name").asText(), row.path("category_name").asText(),
                row.path("category_group_code").asText(), lat, lng,
                row.path("road_address_name").asText().isBlank() ? row.path("address_name").asText()
                        : row.path("road_address_name").asText(), row.path("place_url").asText());
    }
}
