package com.coursepick.controller;

import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.PlaceLookupResponse;
import com.coursepick.kakao.KakaoLocalClient;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.places.PlaceLookupService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlacesController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ObjectProvider<PlaceLookupService> lookupServiceProvider;
    private final KakaoLocalClient kakao;
    private final CoursepickSettings settings;

    public PlacesController(
            ObjectProvider<PlaceLookupService> lookupServiceProvider,
            KakaoLocalClient kakao,
            CoursepickSettings settings) {
        this.lookupServiceProvider = lookupServiceProvider;
        this.kakao = kakao;
        this.settings = settings;
    }

    @GetMapping("/places/search-text")
    public ResponseEntity<?> searchText(@RequestParam String q) {
        PlaceLookupService service = lookupServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다"));
        }
        return service.searchCandidates(q, settings.regionLat(), settings.regionLng())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "구글 검색 실패")));
    }

    @GetMapping("/places/{kakaoId}/match")
    public ResponseEntity<?> match(
            @PathVariable String kakaoId,
            @RequestParam String name,
            @RequestParam String theme,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlaceLookupService service = lookupServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다"));
        }
        Optional<KakaoPlace> place = kakao.searchKeywordByAccuracy(
                        name, settings.regionLat(), settings.regionLng(), settings.regionRadiusM())
                .stream()
                .filter(p -> p.id().equals(kakaoId))
                .findFirst();
        if (place.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "카카오 검색에서 해당 장소를 찾지 못했습니다"));
        }
        LocalDate visitDate = date != null ? date : LocalDate.now(SEOUL);
        PlaceLookupService.Result result = service.lookup(place.get(), theme, visitDate);
        return ResponseEntity.ok(Map.of(
                "kakaoId", kakaoId,
                "decision", result.decision(),
                "source", result.source(),
                "reason", String.valueOf(result.reason()),
                "businessStatus", result.google() == null ? "" : String.valueOf(result.google().businessStatus()),
                "googleMapsUri", result.google() == null ? "" : String.valueOf(result.google().googleMapsUri())));
    }

    @GetMapping("/places/{kakaoId}")
    public ResponseEntity<?> lookup(
            @PathVariable String kakaoId,
            @RequestParam String theme,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String arrive,
            @RequestParam(required = false) String depart) {
        PlaceLookupService service = lookupServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다"));
        }
        LocalDate visitDate = date != null ? date : LocalDate.now(SEOUL);
        LocalDateTime arriveAt = null;
        LocalDateTime departAt = null;
        if (arrive != null && depart != null) {
            arriveAt = visitDate.atTime(parseTime(arrive));
            departAt = visitDate.atTime(parseTime(depart));
            if (!departAt.isAfter(arriveAt)) {
                departAt = departAt.plusDays(1);
            }
        }
        return ResponseEntity.ok(PlaceLookupResponse.from(
                service.lookup(kakaoId, theme, visitDate), visitDate, arriveAt, departAt));
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid time");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "theme은 cafe, food, walk, shopping, history, culture 중 하나, arrive·depart는 HH:mm 형식이어야 합니다"));
    }
}
