package com.coursepick.plan;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StayTimePolicy {

    private final JsonNode stay;
    private final Set<String> largePalaces = new HashSet<>();

    public StayTimePolicy(CoursepickSettings settings) {
        this.stay = settings.root().path("stayMinutes");
        for (JsonNode n : settings.root().path("planning").path("largePalaceNames")) {
            largePalaces.add(n.asText());
        }
    }

    public Stay stayFor(String theme, String placeName) {
        String key = switch (theme) {
            case "history" -> largePalaces.contains(placeName.strip()) ? "palace" : "historySmall";
            case "shopping" -> placeName.contains("거리") || placeName.contains("시장") ? "shoppingStreet" : "shoppingShop";
            default -> theme;
        };
        JsonNode node = stay.path(key);
        return new Stay(node.path("default").asInt(60), node.path("min").asInt(30), node.path("max").asInt(90));
    }
}
