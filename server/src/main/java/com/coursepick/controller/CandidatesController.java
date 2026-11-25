package com.coursepick.controller;

import com.coursepick.common.BadRequestException;
import com.coursepick.candidate.CandidateCollector;
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
    private final CandidateCollector collector;
    private final CoursepickSettings settings;
    private final CoursepickProperties properties;

    public CandidatesController(
            KakaoLocalClient kakao,
            CandidateCollector collector,
            CoursepickSettings settings,
            CoursepickProperties properties) {
        this.kakao = kakao;
        this.collector = collector;
        this.settings = settings;
        this.properties = properties;
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> candidates(
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer inner,
            @RequestParam(required = false) Integer outer,
            @RequestParam(required = false) Integer radius) {
        if (!properties.keys().hasKakaoRest()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "KAKAO_REST_API_KEY가 설정되지 않았습니다"));
        }
        boolean hasTheme = theme != null && !theme.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if ((hasTheme ? 1 : 0) + (hasCategory ? 1 : 0) + (hasKeyword ? 1 : 0) != 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "theme, category, keyword 중 하나만 지정해야 합니다"));
        }
        double centerLat = lat != null ? lat : settings.regionLat();
        double centerLng = lng != null ? lng : settings.regionLng();
        List<KakaoPlace> places;
        try {
            if (hasTheme) {
                int innerHalf = inner != null ? inner : 0;
                int outerHalf = outer != null ? outer : collector.expansionSteps().get(0);
                places = collector.collectRing(theme, centerLat, centerLng, innerHalf, outerHalf);
            } else {
                int r = radius != null ? radius : settings.regionRadiusM();
                places = hasCategory
                        ? kakao.searchCategory(category, centerLat, centerLng, r)
                        : kakao.searchKeyword(keyword, centerLat, centerLng, r);
            }
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("count", places.size(), "places", places));
    }
}
