package com.coursepick.config;

import com.fasterxml.jackson.databind.JsonNode;

public record CoursepickSettings(JsonNode root) {

    public String regionName() {
        return root.path("region").path("name").asText("");
    }

    public int regionRadiusM() {
        return root.path("region").path("radiusM").asInt(0);
    }
}
