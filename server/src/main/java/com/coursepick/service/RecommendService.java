package com.coursepick.service;

import com.coursepick.dto.RecommendRequest;
import com.coursepick.dto.RecommendResponse;
import com.coursepick.dto.RecommendResponse.Course;
import com.coursepick.dto.RecommendResponse.Leg;
import com.coursepick.dto.RecommendResponse.Place;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendService {

    public RecommendResponse recommend(RecommendRequest request) {
        Course course = new Course(
                "c1",
                "basic",
                List.of(
                        new Place(1, "8130598", "경복궁", "history", 37.5796, 126.9770, "14:10", 90, "confirmed"),
                        new Place(2, "1234567", "OO카페", "cafe", 37.5781, 126.9850, "15:55", 60, "unknown")),
                List.of(
                        new Leg("start", 1, "walk", 10),
                        new Leg(1, 2, "transit", 15)),
                175,
                "16:55",
                "relaxed",
                "임시 응답입니다. 추천 로직 연결 전 앱 화면 개발용 가짜 데이터예요");
        return new RecommendResponse(List.of(course), "OK");
    }
}
