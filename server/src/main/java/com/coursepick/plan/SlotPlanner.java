
package com.coursepick.plan;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SlotPlanner {

    private static final List<String> SHORT_THEMES = List.of("walk", "shopping");

    private final JsonNode mealWindows;
    private final JsonNode planning;
    private final JsonNode leftover;

    public SlotPlanner(CoursepickSettings settings) {
        this.mealWindows = settings.root().path("mealWindows");
        this.planning = settings.root().path("planning");
        this.leftover = settings.root().path("leftoverTime");
    }

    public static int parseMinutes(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    public Optional<Slot> next(PlanState state) {
        int returnReserve = planning.path("returnReserveMin").asInt(15);
        int shortMin = leftover.path("extendStayBelowMinutes").asInt(20);
        int normalMin = leftover.path("shortSlotBelowMinutes").asInt(45);
        int remaining = state.endMin() - state.clockMin() - returnReserve;
        if (remaining < shortMin) {
            return Optional.empty();
        }

        if (!"food".equals(state.lastTheme())) {
            String window = dueMealWindow(state);
            if (window != null) {
                return Optional.of(new Slot("food", false, window));
            }
        }

        boolean shortSlot = remaining < normalMin;
        List<String> candidates = candidates(state, shortSlot);
        if (candidates.isEmpty()) {
            candidates = fallback(state, shortSlot);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String chosen = leastUsed(candidates, state.themeCounts());
        return Optional.of(new Slot(chosen, shortSlot, null));
    }

    private int latestStart(JsonNode window, int windowEnd) {
        String configured = window.path("latestStart").asText("");
        return configured.isEmpty() ? windowEnd - 45 : parseMinutes(configured);
    }

    public int mealWindowStart(String name) {
        return parseMinutes(edge(mealWindows.path(name), 0, "from"));
    }

    public String pendingMealWindow(PlanState state) {
        int needOverlap = planning.path("mealOverlapMin").asInt(30);
        Iterator<Map.Entry<String, JsonNode>> it = mealWindows.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (state.mealsDone().contains(e.getKey())) {
                continue;
            }
            int ws = parseMinutes(edge(e.getValue(), 0, "from"));
            int we = parseMinutes(edge(e.getValue(), 1, "to"));
            int overlap = Math.min(state.endMin(), latestStart(e.getValue(), we)) - Math.max(state.courseStartMin(), ws);
            if (overlap >= needOverlap && state.clockMin() <= latestStart(e.getValue(), we)) {
                return e.getKey();
            }
        }
        return null;
    }

    private String dueMealWindow(PlanState state) {
        int needOverlap = planning.path("mealOverlapMin").asInt(30);
        int earlyMin = planning.path("mealEarlyMin").asInt(0);
        Iterator<Map.Entry<String, JsonNode>> it = mealWindows.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (state.mealsDone().contains(e.getKey())) {
                continue;
            }
            int ws = parseMinutes(edge(e.getValue(), 0, "from"));
            int we = parseMinutes(edge(e.getValue(), 1, "to"));
            int overlap = Math.min(state.endMin(), latestStart(e.getValue(), we)) - Math.max(state.courseStartMin(), ws);
            boolean due = state.clockMin() >= ws - earlyMin && state.clockMin() <= latestStart(e.getValue(), we);
            if (overlap >= needOverlap && due) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean allows(PlanState state, String theme) {
        if (theme.equals(state.lastTheme())) {
            return false;
        }
        return !"cafe".equals(theme) || cafeAllowed(state);
    }

    private String edge(JsonNode window, int index, String key) {
        return window.isArray() ? window.get(index).asText() : window.path(key).asText();
    }

    private List<String> candidates(PlanState state, boolean shortSlot) {
        List<String> result = new ArrayList<>();
        for (String theme : state.userThemes()) {
            if (theme.equals(state.lastTheme())) {
                continue;
            }
            if (shortSlot && !SHORT_THEMES.contains(theme)) {
                continue;
            }
            if ("cafe".equals(theme) && !cafeAllowed(state)) {
                continue;
            }
            result.add(theme);
        }
        return result;
    }

    private List<String> fallback(PlanState state, boolean shortSlot) {
        List<String> pool = shortSlot ? List.of("walk") : List.of("walk", "culture", "history", "cafe");
        List<String> result = new ArrayList<>();
        for (String theme : pool) {
            if (theme.equals(state.lastTheme())) {
                continue;
            }
            if ("cafe".equals(theme) && !cafeAllowed(state)) {
                continue;
            }
            result.add(theme);
        }
        return result;
    }

    private boolean cafeAllowed(PlanState state) {
        int maxPerDay = planning.path("cafeMaxPerDay").asInt(3);
        int gapMin = planning.path("cafeMinGapMin").asInt(240);
        int used = state.themeCounts().getOrDefault("cafe", 0);
        if (used >= maxPerDay) {
            return false;
        }
        return state.lastCafeClockMin() < 0 || state.clockMin() - state.lastCafeClockMin() >= gapMin;
    }

    private String leastUsed(List<String> candidates, Map<String, Integer> counts) {
        String best = candidates.get(0);
        for (String theme : candidates) {
            if (counts.getOrDefault(theme, 0) < counts.getOrDefault(best, 0)) {
                best = theme;
            }
        }
        return best;
    }
}
