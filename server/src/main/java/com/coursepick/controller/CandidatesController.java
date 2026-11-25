package com.coursepick.controller;

import com.coursepick.config.CoursepickProperties;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.kakao.KakaoLocalClient;
import com.coursepick.kakao.KakaoPlace;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CandidatesController {

    private final KakaoLocalClient kakao;
    private final CoursepickSettings settings;
    private final CoursepickProperties properties;

    public CandidatesController(KakaoLocalClient kakao, CoursepickSettings settings, CoursepickProperties properties) {
        this.kakao = kakao;
        this.settings = settings;
        this.properties = properties;
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> candidates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer radius) {
        if (!properties.keys().hasKakaoRest()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "KAKAO_REST_API_KEY가 설정되지 않았습니다"));
        }
        if ((category == null || category.isBlank()) == (keyword == null || keyword.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "category(CE7, FD6 등) 또는 keyword 중 하나만 지정해야 합니다"));
        }
        double lat = settings.regionLat();
        double lng = settings.regionLng();
        int r = radius != null ? radius : settings.regionRadiusM();
        List<KakaoPlace> places = category != null && !category.isBlank()
                ? kakao.searchCategory(category, lat, lng, r)
                : kakao.searchKeyword(keyword, lat, lng, r);
        return ResponseEntity.ok(Map.of("count", places.size(), "places", places));
    }
}
