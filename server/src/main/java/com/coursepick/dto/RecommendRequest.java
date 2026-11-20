package com.coursepick.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RecommendRequest(
        @NotBlank String startPointId,
        String date,
        @NotBlank String startTime,
        @NotBlank String endTime,
        String transport,
        @NotEmpty List<String> themes,
        String companion,
        Integer partySize,
        String mobility,
        String ageGroup,
        String mustVisit) {
}
