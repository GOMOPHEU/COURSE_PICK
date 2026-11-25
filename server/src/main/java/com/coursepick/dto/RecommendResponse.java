package com.coursepick.dto;

import java.util.List;

public record RecommendResponse(List<Course> courses, String state, List<String> geminiNotes) {

    public record Course(
            String courseId,
            String source,
            List<Place> places,
            List<Leg> legs,
            int totalMinutes,
            String endAt,
            String fit,
            String reason,
            List<String> notes) {
    }

    public record Place(
            int order,
            String kakaoId,
            String name,
            String theme,
            double lat,
            double lng,
            String arriveAt,
            int stayMinutes,
            String openStatus) {
    }

    public record Leg(Object from, Object to, String mode, int minutes) {
    }
}
