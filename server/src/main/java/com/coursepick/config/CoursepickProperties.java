package com.coursepick.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coursepick")
public record CoursepickProperties(String configPath, Keys keys, Endpoints endpoints, Places places) {

    public record Keys(String kakaoRest, String googlePlaces, String gemini) {

        public boolean hasKakaoRest() {
            return kakaoRest != null && !kakaoRest.isBlank();
        }

        public boolean hasGooglePlaces() {
            return googlePlaces != null && !googlePlaces.isBlank();
        }

        public boolean hasGemini() {
            return gemini != null && !gemini.isBlank();
        }
    }

    public record Endpoints(String kakaoLocal, String googlePlaces) {
    }

    public record Places(String mappingsPath, String officialHoursPath) {
    }
}
