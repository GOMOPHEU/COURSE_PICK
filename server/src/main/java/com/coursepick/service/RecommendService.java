package com.coursepick.service;

import com.coursepick.assemble.CourseAssembler;
import com.coursepick.assemble.CourseAssembler.Pick;
import com.coursepick.config.CoursepickProperties;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.RecommendRequest;
import com.coursepick.dto.RecommendResponse;
import com.coursepick.dto.RecommendResponse.Course;
import com.coursepick.dto.RecommendResponse.Leg;
import com.coursepick.dto.RecommendResponse.Place;
import com.coursepick.plan.SlotPlanner;
import com.coursepick.score.TimeFit;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecommendService {

    private final CourseAssembler assembler;
    private final CoursepickSettings settings;
    private final CoursepickProperties properties;

    public RecommendService(CourseAssembler assembler, CoursepickSettings settings, CoursepickProperties properties) {
        this.assembler = assembler;
        this.settings = settings;
        this.properties = properties;
    }

    public RecommendResponse recommend(RecommendRequest request) {
        if (!properties.keys().hasKakaoRest()) {
            return stub();
        }
        int startMin = parseTime(request.startTime(), "출발 시각");
        int endMin = parseTime(request.endTime(), "종료 시각");
        if (endMin <= startMin) {
            throw new IllegalArgumentException("종료 시각은 출발 시각보다 늦어야 합니다");
        }
        JsonNode startPoint = findStartPoint(request.startPointId());
        LocalDate date = request.date() == null || request.date().isBlank()
                ? LocalDate.now(ZoneId.of("Asia/Seoul"))
                : LocalDate.parse(request.date());

        CourseAssembler.Input input = new CourseAssembler.Input(
                date, startMin, endMin,
                startPoint.path("lat").asDouble(), startPoint.path("lng").asDouble(),
                request.themes(), request.companion(), request.ageGroup(), request.mobility());
        List<Pick> picks = assembler.assemble(input);
        if (picks.isEmpty()) {
            return new RecommendResponse(List.of(), "NO_COURSE");
        }
        return new RecommendResponse(List.of(toCourse(picks, startMin, endMin)), "OK");
    }

    private Course toCourse(List<Pick> picks, int startMin, int endMin) {
        List<Place> places = new ArrayList<>();
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            Pick p = picks.get(i);
            places.add(new Place(i + 1, p.place().id(), p.place().name(), p.theme(), p.place().lat(), p.place().lng(),
                    format(p.arriveMin()), p.stayMin(), p.openStatus()));
            Object from = i == 0 ? "start" : (Object) i;
            legs.add(new Leg(from, i + 1, "walk", p.travelMin()));
        }
        int finishMin = picks.get(picks.size() - 1).departMin();
        int totalMinutes = finishMin - startMin;
        TimeFit fit = TimeFit.of(totalMinutes + assembler.returnReserveMin(), endMin - startMin);
        String fitValue = fit == TimeFit.OVER ? TimeFit.TIGHT.apiValue() : fit.apiValue();
        return new Course("c1", "basic", places, legs, totalMinutes, format(finishMin), fitValue,
                "가까운 곳부터 점수순으로 이어 붙인 기본 추천이에요");
    }

    private JsonNode findStartPoint(String id) {
        for (JsonNode point : settings.root().path("startPoints")) {
            if (point.path("id").asText().equals(id)) {
                return point;
            }
        }
        throw new IllegalArgumentException("알 수 없는 출발 지점입니다: " + id);
    }

    private int parseTime(String value, String label) {
        try {
            return SlotPlanner.parseMinutes(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(label + "은 HH:mm 형식이어야 합니다");
        }
    }

    private String format(int minutes) {
        return String.format("%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }

    private RecommendResponse stub() {
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
                "임시 응답입니다. 카카오 키가 없어 앱 화면 개발용 가짜 데이터를 돌려줘요");
        return new RecommendResponse(List.of(course), "OK");
    }
}
