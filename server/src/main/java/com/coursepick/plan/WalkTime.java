package com.coursepick.plan;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class WalkTime {

    private final double speedMPerMin;
    private final double detourFactor;

    public WalkTime(CoursepickSettings settings) {
        JsonNode planning = settings.root().path("planning");
        this.speedMPerMin = planning.path("walkSpeedMPerMin").asDouble(67);
        this.detourFactor = planning.path("detourFactor").asDouble(1.3);
    }

    public int minutes(double straightDistanceM) {
        return (int) Math.ceil(straightDistanceM * detourFactor / speedMPerMin);
    }
}
