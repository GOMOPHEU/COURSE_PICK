package com.coursepick.config;

import com.fasterxml.jackson.databind.JsonNode;

public record CoursepickSettings(JsonNode root) {

    public String regionName() {
        return root.path("region").path("name").asText("");
    }

    public int regionRadiusM() {
        JsonNode region = root.path("region");
        int value = region.path("radiusM").asInt(0);
        return value > 0 ? value : region.path("radiusMeters").asInt(0);
    }

    public double regionLat() {
        JsonNode region = root.path("region");
        return region.has("center") ? region.path("center").path("lat").asDouble() : region.path("lat").asDouble();
    }

    public double regionLng() {
        JsonNode region = root.path("region");
        return region.has("center") ? region.path("center").path("lng").asDouble() : region.path("lng").asDouble();
    }
}
