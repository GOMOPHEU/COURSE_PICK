package com.coursepick.service;

import com.coursepick.common.BadRequestException;
import com.coursepick.assemble.CourseAssembler;
import com.coursepick.assemble.CourseAssembler.Pick;
import com.coursepick.config.CoursepickProperties;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.RecommendRequest;
import com.coursepick.gemini.GeminiRanker;
import com.coursepick.dto.RecommendResponse;
import com.coursepick.dto.RecommendResponse.Course;
import com.coursepick.dto.RecommendResponse.Leg;
import com.coursepick.dto.RecommendResponse.Place;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.plan.SlotPlanner;
import com.coursepick.route.RouteException;
import com.coursepick.route.RouteVerifier;
import com.coursepick.score.TimeFit;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecommendService {
    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);


    private final CourseAssembler assembler;
    private final CoursepickSettings settings;
    private final CoursepickProperties properties;
    private final RouteVerifier routeVerifier;
    private final GeminiRanker geminiRanker;

    public RecommendService(
            CourseAssembler assembler, CoursepickSettings settings, CoursepickProperties properties,
            RouteVerifier routeVerifier, GeminiRanker geminiRanker) {
        this.routeVerifier = routeVerifier;
        this.geminiRanker = geminiRanker;
        this.assembler = assembler;
        this.settings = settings;
        this.properties = properties;
    }

    public RecommendResponse recommend(RecommendRequest request) {
        int startMin = parseTime(request.startTime(), "출발 시각");
        int endMin = parseTime(request.endTime(), "종료 시각");
        if (endMin <= startMin) {
            throw new BadRequestException("종료 시각은 출발 시각보다 늦어야 합니다");
        }
        if (request.date() != null && !request.date().isBlank()) {
            LocalDate.parse(request.date());
        }
        if (!properties.keys().hasKakaoRest()) {
            return stub();
        }
        JsonNode startPoint = findStartPoint(request.startPointId());
        LocalDate date = request.date() == null || request.date().isBlank()
                ? LocalDate.now(ZoneId.of("Asia/Seoul"))
                : LocalDate.parse(request.date());

        CourseAssembler.Input input = new CourseAssembler.Input(
                date, startMin, endMin,
                startPoint.path("lat").asDouble(), startPoint.path("lng").asDouble(),
                request.themes(), request.companion(), request.ageGroup(), request.mobility());
        JsonNode planning = settings.root().path("planning");
        int maxCourses = planning.path("maxCourses").asInt(3);
        int minPlaces = planning.path("minPlacesPerCourse").asInt(2);
        boolean transit = "transit".equalsIgnoreCase(request.transport());
        boolean verifyRoutes = settings.root().path("routeVerify").path("enabled").asBoolean(true);

        KakaoPlace mustVisit = null;
        if (request.mustVisit() != null && !request.mustVisit().isBlank()) {
            mustVisit = assembler.findPlace(request.mustVisit())
                    .orElseThrow(() -> new BadRequestException("필수 방문지를 이 지역에서 찾을 수 없습니다"));
        }
        List<Course> courses = new ArrayList<>();
        List<String> geminiNotes = new ArrayList<>();
        Set<String> excluded = new HashSet<>();
        boolean routeFailed = false;
        boolean useGemini = geminiRanker.available();
        int target = useGemini ? Math.max(maxCourses, geminiRanker.candidateCourses()) : maxCourses;
        CourseAssembler.Budget budget = new CourseAssembler.Budget();
        for (int n = 0; n < target && courses.size() < target; n++) {
            CourseAssembler.Assembly assembly = assembler.assembleWithNotes(input, excluded, mustVisit, budget);
            List<Pick> picks = assembly.picks();
            if (picks.isEmpty()) {
                break;
            }
            Set<String> assembledThemes = new HashSet<>();
            for (Pick p : picks) {
                assembledThemes.add(p.theme());
            }
            for (Pick p : picks) {
                if (mustVisit == null || !p.place().id().equals(mustVisit.id())) {
                    excluded.add(p.place().id());
                }
            }
            Map<Integer, String> modes = Map.of();
            if (verifyRoutes) {
                try {
                    RouteVerifier.Result verified = routeVerifier.verify(
                            picks, input.startLat(), input.startLng(), startMin, endMin,
                            assembler.returnReserveMin(), transit);
                    picks = verified.picks();
                    modes = verified.modes();
                } catch (RouteException e) {
                    routeFailed = true;
                    break;
                }
            }
            if (picks.isEmpty() || (!courses.isEmpty() && picks.size() < minPlaces)) {
                continue;
            }
            String id = "c" + (courses.size() + 1);
            String reason = courses.isEmpty()
                    ? "가까운 곳부터 점수순으로 이어 붙인 기본 추천이에요"
                    : "앞선 코스와 장소가 겹치지 않는 다른 구성의 추천이에요";
            List<String> notes = missingThemeNotes(input.themes(), picks, assembledThemes, assembly.notes());
            courses.add(toCourse(id, "basic", reason, picks, startMin, endMin, modes, notes));
        }
        if (courses.isEmpty()) {
            return new RecommendResponse(List.of(), routeFailed ? "ROUTE_FAIL" : "NO_COURSE", geminiNotes);
        }
        if (useGemini && courses.size() > 1) {
            GeminiRanker.Result ranked = geminiRanker.rank(input, courses, maxCourses);
            geminiNotes.addAll(ranked.notes());
            courses = applyChoices(courses, ranked.choices(), maxCourses);
        } else if (courses.size() > maxCourses) {
            courses = renumber(new ArrayList<>(courses.subList(0, maxCourses)));
        }
        log.info("구글 사용량: 상세 {}건, 검색 {}건, 코스 {}개", budget.googleCalls(), budget.searchCalls(), courses.size());
        return new RecommendResponse(courses, "OK", geminiNotes);
    }

    private List<Course> applyChoices(List<Course> courses, List<GeminiRanker.Choice> choices, int maxCourses) {
        List<Course> ordered = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (GeminiRanker.Choice choice : choices) {
            Course base = courses.get(choice.index());
            String reason = choice.reason().isBlank() ? base.reason() : choice.reason();
            ordered.add(new Course(base.courseId(), "ai", base.places(), base.legs(), base.totalMinutes(),
                    base.endAt(), base.fit(), reason, base.notes()));
            used.add(choice.index());
        }
        for (int i = 0; i < courses.size() && ordered.size() < maxCourses; i++) {
            if (!used.contains(i)) {
                ordered.add(courses.get(i));
            }
        }
        return renumber(ordered);
    }

    private List<Course> renumber(List<Course> list) {
        List<Course> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Course c = list.get(i);
            result.add(new Course("c" + (i + 1), c.source(), c.places(), c.legs(), c.totalMinutes(), c.endAt(),
                    c.fit(), c.reason(), c.notes()));
        }
        return result;
    }

    private List<String> missingThemeNotes(
            List<String> themes, List<Pick> picks, Set<String> assembledThemes, Map<String, String> notes) {
        Set<String> finalThemes = new HashSet<>();
        for (Pick p : picks) {
            finalThemes.add(p.theme());
        }
        List<String> result = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (String theme : themes) {
            if (finalThemes.contains(theme) || !done.add(theme)) {
                continue;
            }
            String text = assembledThemes.contains(theme)
                    ? CourseAssembler.themeLabel(theme) + ": 이동 시간 검증 과정에서 일정에서 빠졌어요"
                    : notes.get(theme);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    private Course toCourse(
            String id, String source, String reason, List<Pick> picks, int startMin, int endMin, Map<Integer, String> modes,
            List<String> notes) {
        List<Place> places = new ArrayList<>();
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            Pick p = picks.get(i);
            places.add(new Place(i + 1, p.place().id(), p.place().name(), p.theme(), p.place().lat(), p.place().lng(),
                    format(p.arriveMin()), p.stayMin(), p.openStatus()));
            Object from = i == 0 ? "start" : (Object) i;
            legs.add(new Leg(from, i + 1, modes.getOrDefault(i, "walk"), p.travelMin()));
        }
        int finishMin = picks.get(picks.size() - 1).departMin();
        int totalMinutes = finishMin - startMin;
        TimeFit fit = TimeFit.of(totalMinutes + assembler.returnReserveMin(), endMin - startMin);
        String fitValue = fit == TimeFit.OVER ? TimeFit.TIGHT.apiValue() : fit.apiValue();
        return new Course(id, source, places, legs, totalMinutes, format(finishMin), fitValue, reason, notes);
    }

    private JsonNode findStartPoint(String id) {
        for (JsonNode point : settings.root().path("startPoints")) {
            if (point.path("id").asText().equals(id)) {
                return point;
            }
        }
        throw new BadRequestException("알 수 없는 출발 지점입니다: " + id);
    }

    private int parseTime(String value, String label) {
        try {
            return SlotPlanner.parseMinutes(value);
        } catch (RuntimeException e) {
            throw new BadRequestException(label + "은 HH:mm 형식이어야 합니다");
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
                "임시 응답입니다. 카카오 키가 없어 앱 화면 개발용 가짜 데이터를 돌려줘요",
                List.of());
        return new RecommendResponse(List.of(course), "OK", List.of());
    }
}
