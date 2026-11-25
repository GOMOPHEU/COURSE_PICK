package com.coursepick.controller;

import com.coursepick.dto.RecommendRequest;
import com.coursepick.dto.RecommendResponse;
import com.coursepick.service.RecommendService;
import jakarta.validation.Valid;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping("/recommend")
    public RecommendResponse recommend(@Valid @RequestBody RecommendRequest request) {
        return recommendService.recommend(request);
    }

    @ExceptionHandler({IllegalArgumentException.class, DateTimeParseException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> upstreamFailure(RestClientException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "외부 API 조회에 실패했습니다"));
    }
}
