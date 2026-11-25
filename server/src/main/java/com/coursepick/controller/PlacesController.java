package com.coursepick.controller;

import com.coursepick.dto.PlaceLookupResponse;
import com.coursepick.places.PlaceLookupService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
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

    public PlacesController(ObjectProvider<PlaceLookupService> lookupServiceProvider) {
        this.lookupServiceProvider = lookupServiceProvider;
    }

    @GetMapping("/places/{kakaoId}")
    public ResponseEntity<?> lookup(
            @PathVariable String kakaoId,
            @RequestParam String theme,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PlaceLookupService service = lookupServiceProvider.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다"));
        }
        LocalDate visitDate = date != null ? date : LocalDate.now(SEOUL);
        return ResponseEntity.ok(PlaceLookupResponse.from(service.lookup(kakaoId, theme, visitDate), visitDate));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "theme은 cafe, food, walk, shopping, history, culture 중 하나여야 합니다"));
    }
}
