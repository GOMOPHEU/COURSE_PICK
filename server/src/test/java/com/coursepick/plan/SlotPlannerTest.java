package com.coursepick.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotPlannerTest {

    private static final String CONFIG = """
            {
              "mealWindows": {
                "lunch": {"from": "11:00", "to": "14:30"},
                "dinner": {"from": "17:00", "to": "20:30"}
              },
              "walkingEstimate": {"detourFactor": 1.3, "metersPerMinute": 67},
              "leftoverTime": {"extendStayBelowMinutes": 20, "shortSlotBelowMinutes": 45},
              "stayMinutes": {
                "cafe": {"default": 60, "min": 40, "max": 90},
                "palace": {"default": 90, "min": 60, "max": 120},
                "heritageSmall": {"default": 30, "min": 20, "max": 45}
              },
              "planning": {
                "returnReserveMin": 15, "mealOverlapMin": 30,
                "cafeMaxPerDay": 3, "cafeMinGapMin": 240,
                "largePalaceNames": ["경복궁", "창덕궁"]
              }
            }
            """;

    private SlotPlanner planner;
    private StayTimePolicy stay;
    private WalkTime walk;

    @BeforeEach
    void setUp() throws Exception {
        CoursepickSettings settings = new CoursepickSettings(new ObjectMapper().readTree(CONFIG));
        planner = new SlotPlanner(settings);
        stay = new StayTimePolicy(settings);
        walk = new WalkTime(settings);
    }

    private PlanState state(int start, int clock, int end, String last, int lastCafe, Map<String, Integer> counts,
            Set<String> meals, List<String> themes) {
        return new PlanState(start, clock, end, last, lastCafe, counts, meals, themes);
    }

    private static int t(String hhmm) {
        return SlotPlanner.parseMinutes(hhmm);
    }

    @Test
    void firstSlotFollowsUserThemeOrder() {
        PlanState s = state(t("14:00"), t("14:00"), t("19:00"), null, -1, Map.of(), Set.of(), List.of("cafe", "history"));
        assertThat(planner.next(s).get().theme()).isEqualTo("cafe");
    }

    @Test
    void sameThemeIsNotRepeated() {
        PlanState s = state(t("14:00"), t("15:30"), t("19:00"), "cafe", t("14:00"), Map.of("cafe", 1), Set.of(),
                List.of("cafe", "history"));
        assertThat(planner.next(s).get().theme()).isEqualTo("history");
    }

    @Test
    void mealIsInsertedWhenCourseOverlapsMealWindow() {
        PlanState s = state(t("15:00"), t("16:45"), t("21:00"), "history", -1, Map.of("history", 1), Set.of(),
                List.of("history", "culture"));
        Slot slot = planner.next(s).get();
        assertThat(slot.theme()).isEqualTo("food");
        assertThat(slot.mealWindow()).isEqualTo("dinner");
    }

    @Test
    void mealIsNotRepeatedOnceDone() {
        PlanState s = state(t("15:00"), t("18:30"), t("21:00"), "culture", -1, Map.of("food", 1), Set.of("dinner"),
                List.of("history", "culture"));
        assertThat(planner.next(s).get().theme()).isEqualTo("history");
    }

    @Test
    void cafeWaitsFourHoursBetweenVisits() {
        PlanState s = state(t("10:00"), t("12:00"), t("19:00"), "history", t("10:00"), Map.of("cafe", 1, "history", 1),
                Set.of("lunch"), List.of("cafe", "history", "walk"));
        assertThat(planner.next(s).get().theme()).isEqualTo("walk");
    }

    @Test
    void shortRemainderUsesShortThemes() {
        PlanState s = state(t("14:00"), t("17:35"), t("18:30"), "cafe", t("14:00"), Map.of("cafe", 1), Set.of("dinner"),
                List.of("cafe", "history", "walk"));
        Slot slot = planner.next(s).get();
        assertThat(slot.shortSlot()).isTrue();
        assertThat(slot.theme()).isEqualTo("walk");
    }

    @Test
    void tinyRemainderMeansNoMoreSlots() {
        PlanState s = state(t("14:00"), t("18:20"), t("18:50"), "cafe", t("14:00"), Map.of("cafe", 1), Set.of(),
                List.of("cafe", "history"));
        assertThat(planner.next(s)).isEmpty();
    }

    @Test
    void stayTimeDependsOnPlaceType() {
        assertThat(stay.stayFor("history", "경복궁").defaultMin()).isEqualTo(90);
        assertThat(stay.stayFor("history", "운현궁").defaultMin()).isEqualTo(30);
        assertThat(stay.stayFor("cafe", "아무카페").maxMin()).isEqualTo(90);
    }

    @Test
    void walkTimeUsesDetourAndSpeed() {
        assertThat(walk.minutes(500)).isEqualTo(10);
        assertThat(walk.minutes(0)).isZero();
    }
}
