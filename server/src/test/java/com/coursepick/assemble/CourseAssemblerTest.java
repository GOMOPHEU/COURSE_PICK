package com.coursepick.assemble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coursepick.assemble.CourseAssembler.Pick;
import com.coursepick.candidate.CandidateCollector;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.geo.Rect;
import com.coursepick.kakao.KakaoLocalClient;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.kakao.KakaoSearchResult;
import com.coursepick.places.PlaceLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.coursepick.plan.SlotPlanner;
import com.coursepick.plan.StayTimePolicy;
import com.coursepick.plan.WalkTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CourseAssemblerTest {

    private static final double LAT = 37.5765;
    private static final double LNG = 126.9854;

    private static final String CONFIG = """
            {
              "region": {"name": "안국역", "center": {"lat": 37.5765, "lng": 126.9854}, "radiusMeters": 1300},
              "startPoints": [{"id": "anguk-stn", "name": "안국역", "lat": 37.5765, "lng": 126.9854}],
              "stayMinutes": {
                "cafe": {"default": 60, "min": 40, "max": 90},
                "food": {"default": 60, "min": 45, "max": 90},
                "walk": {"default": 40, "min": 20, "max": 60},
                "palace": {"default": 90, "min": 60, "max": 120},
                "heritageSmall": {"default": 30, "min": 20, "max": 45},
                "culture": {"default": 60, "min": 45, "max": 90},
                "shoppingStreet": {"default": 40, "min": 20, "max": 60},
                "shoppingShop": {"default": 20, "min": 15, "max": 30}
              },
              "mealWindows": {
                "lunch": {"from": "11:00", "to": "14:30"},
                "dinner": {"from": "17:00", "to": "20:30"}
              },
              "leftoverTime": {"extendStayBelowMinutes": 20, "shortSlotBelowMinutes": 45},
              "walkingEstimate": {"detourFactor": 1.3, "metersPerMinute": 67},
              "candidateSearch": {
                "stepsM": [100, 300, 700], "pages": 2, "minEligible": 3,
                "maxSplitDepth": 0, "maxRequestsPerStep": 30,
                "themes": {
                  "cafe": {"categoryCodes": ["CE7"], "keywords": [], "allowedCategoryPrefixes": ["음식점 > 카페"]},
                  "food": {"categoryCodes": ["FD6"], "keywords": [], "allowedCategoryPrefixes": ["음식점 >"],
                           "excludeCategoryPrefixes": ["음식점 > 카페"]},
                  "walk": {"categoryCodes": ["AT4"], "keywords": [],
                           "allowedCategoryPrefixes": ["여행 > 관광,명소 > 테마거리"]},
                  "shopping": {"categoryCodes": [], "keywords": []},
                  "history": {"categoryCodes": ["AT4"], "keywords": [],
                              "allowedCategoryPrefixes": ["여행 > 관광,명소 > 문화유적"],
                              "collapseNamePrefix": true},
                  "culture": {"categoryCodes": ["CT1"], "keywords": [],
                              "allowedCategoryPrefixes": ["문화,예술 > 문화시설"]}
                }
              },
              "rating": {"m": 50},
              "weights": {"distance": 0.25, "rating": 0.3, "theme": 0.15, "companion": 0.15,
                          "openVerified": 0.1, "review": 0.05},
              "reviewBonus": true,
              "duplicateRule": {"excludeIfNameStartsWithChosen": true, "sameCategoryWithinMeters": 300},
              "barRule": {"categoryContains": ["음식점 > 술집"], "nameKeywords": ["포차"]},
              "companionRules": {"family": {"excludeBars": true, "maxLegMinutes": 15}},
              "ageGroupRules": {},
              "mobilityRules": {},
              "closedDays": [{"name": "창덕궁", "weekday": "MON"}],
              "planning": {
                "returnReserveMin": 15, "mealOverlapMin": 30, "cafeMaxPerDay": 3, "cafeMinGapMin": 240,
                "largePalaceNames": ["창덕궁"], "requireGoogleMatch": true, "maxPlaces": 8, "maxVerifyPerSlot": 20,
                "maxGoogleCallsPerRequest": 30
              }
            }
            """;

    private KakaoLocalClient kakao;
    private CourseAssembler assembler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        kakao = mock(KakaoLocalClient.class, invocation -> new KakaoSearchResult(0, List.of()));
        CoursepickSettings settings = new CoursepickSettings(new ObjectMapper().readTree(CONFIG));
        ObjectProvider<PlaceLookupService> noGoogle = mock(ObjectProvider.class);
        assembler = new CourseAssembler(
                settings,
                new CandidateCollector(kakao, settings),
                new SlotPlanner(settings),
                new StayTimePolicy(settings),
                new WalkTime(settings),
                noGoogle);
    }

    private KakaoPlace place(String id, String name, String category, double dLat, double dLng) {
        return new KakaoPlace(id, name, category, "", "", "", LAT + dLat, LNG + dLng, 0, "");
    }

    private CourseAssembler.Input input(String date, String start, String end, List<String> themes, String companion) {
        return new CourseAssembler.Input(
                LocalDate.parse(date), SlotPlanner.parseMinutes(start), SlotPlanner.parseMinutes(end),
                LAT, LNG, themes, companion, null, null);
    }

    private void stubCategory(String code, KakaoPlace... places) {
        when(kakao.searchCategoryRect(eq(code), any(Rect.class), eq(2)))
                .thenReturn(new KakaoSearchResult(places.length, List.of(places)));
    }

    @Test
    void buildsCafeThenHistoryWithoutOverlap() {
        stubCategory("CE7",
                place("c1", "카페A", "음식점 > 카페", 0.0003, 0.0003),
                place("c2", "카페B", "음식점 > 카페", 0.0005, -0.0004),
                place("c3", "카페C", "음식점 > 카페", -0.0004, 0.0006));
        stubCategory("AT4",
                place("h1", "운현궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0004, 0.0016),
                place("h2", "창덕궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0030, 0.0050));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "14:00", "19:00", List.of("cafe", "history"), null));

        assertThat(picks.size()).isGreaterThanOrEqualTo(2);
        assertThat(picks.get(0).theme()).isEqualTo("cafe");
        assertThat(picks.get(1).theme()).isEqualTo("history");
        assertThat(new HashSet<>(picks.stream().map(p -> p.place().id()).toList())).hasSameSizeAs(picks);
        for (int i = 1; i < picks.size(); i++) {
            assertThat(picks.get(i).arriveMin()).isGreaterThanOrEqualTo(picks.get(i - 1).departMin());
        }
        int last = picks.get(picks.size() - 1).departMin();
        assertThat(last).isLessThanOrEqualTo(SlotPlanner.parseMinutes("19:00") - 15);
    }

    @Test
    void skipsPalaceOnItsClosedWeekday() {
        stubCategory("CE7", place("c1", "카페A", "음식점 > 카페", 0.0003, 0.0003));
        stubCategory("AT4", place("h2", "창덕궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0030, 0.0050));

        List<Pick> picks = assembler.assemble(input("2026-11-30", "14:00", "19:00", List.of("cafe", "history"), null));

        assertThat(picks).extracting(p -> p.place().name()).doesNotContain("창덕궁");
    }

    @Test
    void familyCourseExcludesBars() {
        stubCategory("FD6",
                place("f1", "한밤술집", "음식점 > 술집 > 호프,요리주점", 0.0003, 0.0003),
                place("f2", "한식당", "음식점 > 한식", 0.0004, -0.0003));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "11:30", "14:30", List.of("food"), "family"));

        assertThat(picks).isNotEmpty();
        assertThat(picks.get(0).place().name()).isEqualTo("한식당");
        assertThat(picks).extracting(p -> p.place().name()).doesNotContain("한밤술집");
    }

    @Test
    void placesDinnerWhenNothingElseFitsBeforeIt() {
        stubCategory("AT4", place("h2", "창덕궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0030, 0.0050));
        stubCategory("FD6", place("f2", "한식당", "음식점 > 한식", 0.0004, -0.0003));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "14:00", "19:00", List.of("history"), null));

        assertThat(picks).extracting(Pick::theme).contains("history", "food");
    }

    @Test
    void shrinksPalaceStayToLeaveRoomForDinner() {
        stubCategory("AT4", place("h2", "창덕궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0030, 0.0050));
        stubCategory("FD6", place("f2", "한식당", "음식점 > 한식", 0.0004, -0.0003));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "16:30", "19:00", List.of("history"), null));

        assertThat(picks).extracting(Pick::theme).containsExactly("history", "food");
        assertThat(picks.get(0).stayMin()).isEqualTo(60);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dropsCandidatesWithoutGoogleMatch() throws Exception {
        String openAllDay = """
                {"kakaoId": "h1", "verificationStatus": "verified",
                 "hours": {"weekly": [{"days": [1,2,3,4,5,6,7], "open": "09:00", "close": "21:00"}]},
                 "closedDays": {"isoWeekdays": [], "exceptions": []}, "sources": []}
                """;
        JsonNode official = new ObjectMapper().readTree(openAllDay);
        PlaceLookupService lookup = mock(PlaceLookupService.class);
        when(lookup.lookup(eq("h1"), eq("history"), any(LocalDate.class)))
                .thenReturn(new PlaceLookupService.Result("h1", "CHECK_HOURS", "official", "R", null, official));
        when(lookup.lookup(eq("h2"), eq("history"), any(LocalDate.class)))
                .thenReturn(new PlaceLookupService.Result("h2", "KEEP_UNKNOWN", "unknown", "MAPPING_NOT_BUILT", null, null));
        ObjectProvider<PlaceLookupService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lookup);
        CoursepickSettings settings = new CoursepickSettings(new ObjectMapper().readTree(CONFIG));
        CourseAssembler strict = new CourseAssembler(
                settings, new CandidateCollector(kakao, settings), new SlotPlanner(settings),
                new StayTimePolicy(settings), new WalkTime(settings), provider);
        stubCategory("AT4",
                place("h1", "운현궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0004, 0.0016),
                place("h2", "이름없는유적", "여행 > 관광,명소 > 문화유적", 0.0002, 0.0002));

        List<Pick> picks = strict.assemble(input("2026-11-28", "14:00", "16:00", List.of("history"), null));

        assertThat(picks).extracting(p -> p.place().id()).containsExactly("h1");
        assertThat(picks.get(0).openStatus()).isEqualTo("confirmed");
    }

    @Test
    void movesDinnerBeforeFarPlaceWhenNoFoodIsNearIt() {
        stubCategory("CE7", place("c1", "카페A", "음식점 > 카페", 0.0003, 0.0003));
        stubCategory("AT4", place("h1", "운현궁", "여행 > 관광,명소 > 문화유적 > 고궁,궁", 0.0030, 0.0128));
        KakaoPlace food = place("f1", "한식당", "음식점 > 한식", -0.0004, -0.0005);
        when(kakao.searchCategoryRect(eq("FD6"), any(Rect.class), eq(2))).thenAnswer(invocation -> {
            Rect r = invocation.getArgument(1);
            boolean inside = food.lng() >= r.west() && food.lng() <= r.east()
                    && food.lat() >= r.south() && food.lat() <= r.north();
            return new KakaoSearchResult(inside ? 1 : 0, inside ? List.of(food) : List.of());
        });

        List<Pick> picks = assembler.assemble(input("2026-11-28", "15:00", "20:00", List.of("cafe", "history"), null));

        assertThat(picks).extracting(Pick::theme).startsWith("cafe", "food");
        assertThat(picks.get(1).arriveMin()).isGreaterThanOrEqualTo(SlotPlanner.parseMinutes("16:30"));
    }

    @Test
    void stretchesLastActivityToFiveWhenItEndsAfterFourThirty() {
        stubCategory("CE7", place("c1", "카페A", "음식점 > 카페", 0.0003, 0.0003));
        stubCategory("FD6", place("f2", "한식당", "음식점 > 한식", 0.0004, -0.0003));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "15:30", "20:00", List.of("cafe"), null));

        assertThat(picks).extracting(Pick::theme).startsWith("cafe", "food");
        assertThat(picks.get(0).departMin()).isEqualTo(SlotPlanner.parseMinutes("17:00"));
    }

    @Test
    void returnsEmptyWhenNoCandidatesExist() {
        List<Pick> picks = assembler.assemble(input("2026-11-28", "14:00", "19:00", List.of("cafe", "history"), null));

        assertThat(picks).isEmpty();
    }

    @Test
    void lastStayIsExtendedWithinMaximum() {
        stubCategory("CE7", place("c1", "카페A", "음식점 > 카페", 0.0003, 0.0003));

        List<Pick> picks = assembler.assemble(input("2026-11-28", "14:00", "19:00", List.of("cafe"), null));

        assertThat(picks).isNotEmpty();
        Pick last = picks.get(picks.size() - 1);
        assertThat(last.stayMin()).isLessThanOrEqualTo(90);
    }
}
