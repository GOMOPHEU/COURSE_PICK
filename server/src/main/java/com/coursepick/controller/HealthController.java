package com.coursepick.controller;

import com.coursepick.config.CoursepickProperties;
import com.coursepick.config.CoursepickSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final CoursepickProperties properties;
    private final CoursepickSettings settings;

    public HealthController(CoursepickProperties properties, CoursepickSettings settings) {
        this.properties = properties;
        this.settings = settings;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("region", settings.regionName());
        body.put("radiusM", settings.regionRadiusM());
        Map<String, Boolean> keys = new LinkedHashMap<>();
        keys.put("kakaoRest", properties.keys().hasKakaoRest());
        keys.put("googlePlaces", properties.keys().hasGooglePlaces());
        keys.put("gemini", properties.keys().hasGemini());
        body.put("keysConfigured", keys);
        return body;
    }
}
