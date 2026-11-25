package com.coursepick.assemble;

import com.coursepick.candidate.CandidateCollector;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.PlaceLookupResponse;
import com.coursepick.geo.Geo;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.places.PlaceLookupService;
import com.coursepick.plan.PlanState;
import com.coursepick.plan.Slot;
import com.coursepick.plan.SlotPlanner;
import com.coursepick.plan.Stay;
import com.coursepick.plan.StayTimePolicy;
import com.coursepick.plan.WalkTime;
import com.coursepick.score.Candidate;
import com.coursepick.score.RatingCorrector;
import com.coursepick.score.ScoreCalculator;
import com.coursepick.score.ScoreWeights;
import com.coursepick.score.ScoredCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class CourseAssembler {

    public record Input(
            LocalDate date,
            int startMin,
            int endMin,
            double startLat,
            double startLng,
            List<String> themes,
            String companion,
            String ageGroup,
            String mobility) {
    }

    public record Pick(
            KakaoPlace place,
            String theme,
            int travelMin,
            int arriveMin,
            int stayMin,
            Stay stay,
            String openStatus) {

        public int departMin() {
            return arriveMin + stayMin;
        }
    }

    private record Eligible(
            KakaoPlace place,
            int travelMin,
            int arriveMin,
            int stayMin,
            Stay stay,
            String openStatus,
            Double rating,
            Integer reviews) {

        Eligible verified(String status, Double newRating, Integer newReviews) {
            return new Eligible(place, travelMin, arriveMin, stayMin, stay, status, newRating, newReviews);
        }
    }

    private static final class Context {
        final Input input;
        final int reserveMin;
        final PlaceLookupService lookup;
        final ScoreCalculator calculator;
        int googleCalls;

        Context(Input input, int reserveMin, PlaceLookupService lookup, ScoreCalculator calculator) {
            this.input = input;
            this.reserveMin = reserveMin;
            this.lookup = lookup;
            this.calculator = calculator;
        }
    }

    private final CoursepickSettings settings;
    private final CandidateCollector collector;
    private final SlotPlanner planner;
    private final StayTimePolicy stayPolicy;
    private final WalkTime walkTime;
    private final ObjectProvider<PlaceLookupService> lookupProvider;

    public CourseAssembler(
            CoursepickSettings settings,
            CandidateCollector collector,
            SlotPlanner planner,
            StayTimePolicy stayPolicy,
            WalkTime walkTime,
            ObjectProvider<PlaceLookupService> lookupProvider) {
        this.settings = settings;
        this.collector = collector;
        this.planner = planner;
        this.stayPolicy = stayPolicy;
        this.walkTime = walkTime;
        this.lookupProvider = lookupProvider;
    }

    public int returnReserveMin() {
        return settings.root().path("planning").path("returnReserveMin").asInt(15);
    }

    public List<Pick> assemble(Input in) {
        JsonNode planning = settings.root().path("planning");
        int maxPlaces = planning.path("maxPlaces").asInt(8);
        Context ctx = new Context(in, returnReserveMin(), lookupProvider.getIfAvailable(), calculator());

        List<Pick> picks = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        Set<String> meals = new HashSet<>();
        int clock = in.startMin();
        String last = null;
        int lastCafe = -1;
        double lat = in.startLat();
        double lng = in.startLng();

        while (picks.size() < maxPlaces) {
            PlanState state = new PlanState(
                    in.startMin(), clock, in.endMin(), last, lastCafe, Map.copyOf(counts), Set.copyOf(meals), in.themes());
            String pending = planner.pendingMealWindow(state);
            boolean mealPending = pending != null;
            Slot slot = planner.next(state).orElse(null);
            if (slot == null && mealPending && !"food".equals(last)) {
                slot = new Slot("food", false, pending);
            }
            if (slot == null) {
                break;
            }
            if (slot.mealWindow() != null) {
                meals.add(slot.mealWindow());
            }
            String theme = slot.theme();
            Eligible chosen = choose(ctx, theme, slot.shortSlot(), lat, lng, clock, picks, used, mealPending);
            if (chosen == null && slot.mealWindow() != null) {
                chosen = rescueMeal(ctx, slot.mealWindow(), picks, used, counts);
                if (chosen == null) {
                    continue;
                }
                theme = "food";
                Cursor c = cursor(in, picks, used, counts);
                clock = c.clock();
                last = c.last();
                lastCafe = c.lastCafe();
                lat = c.lat();
                lng = c.lng();
            }
            if (chosen == null) {
                for (String alt : in.themes()) {
                    if (alt.equals(slot.theme()) || alt.equals(last)) {
                        continue;
                    }
                    chosen = choose(ctx, alt, slot.shortSlot(), lat, lng, clock, picks, used, mealPending);
                    if (chosen != null) {
                        theme = alt;
                        break;
                    }
                }
            }
            if (chosen == null && mealPending && !"food".equals(last)) {
                chosen = choose(ctx, "food", false, lat, lng, clock, picks, used, false);
                if (chosen != null) {
                    theme = "food";
                    meals.add(pending);
                }
            }
            if (chosen == null && mealPending && !"food".equals(last)) {
                chosen = rescueMeal(ctx, pending, picks, used, counts);
                if (chosen != null) {
                    theme = "food";
                    meals.add(pending);
                    Cursor c = cursor(in, picks, used, counts);
                    clock = c.clock();
                    last = c.last();
                    lastCafe = c.lastCafe();
                    lat = c.lat();
                    lng = c.lng();
                }
            }
            if (chosen == null) {
                break;
            }
            Pick pick = new Pick(chosen.place(), theme, chosen.travelMin(), chosen.arriveMin(), chosen.stayMin(),
                    chosen.stay(), chosen.openStatus());
            picks.add(pick);
            used.add(pick.place().id());
            counts.merge(theme, 1, Integer::sum);
            if ("cafe".equals(theme)) {
                lastCafe = pick.arriveMin();
            }
            clock = pick.departMin();
            last = theme;
            lat = pick.place().lat();
            lng = pick.place().lng();
            if (!"food".equals(theme) && !meals.contains("dinner")) {
                PlanState after = new PlanState(in.startMin(), clock, in.endMin(), last, lastCafe,
                        Map.copyOf(counts), Set.copyOf(meals), in.themes());
                if ("dinner".equals(planner.pendingMealWindow(after))) {
                    int split = SlotPlanner.parseMinutes(planning.path("dinnerPlan").path("splitAt").asText("16:30"));
                    int early = planner.mealWindowStart("dinner");
                    if (clock >= split && clock < early) {
                        stretchTail(picks, early);
                        clock = picks.get(picks.size() - 1).departMin();
                    }
                }
            }
        }
        return extendLast(picks, in.endMin(), ctx.reserveMin);
    }

    private record Cursor(int clock, String last, int lastCafe, double lat, double lng) {}

    private Cursor cursor(Input in, List<Pick> picks, Set<String> used, Map<String, Integer> counts) {
        used.clear();
        counts.clear();
        int lastCafe = -1;
        for (Pick p : picks) {
            used.add(p.place().id());
            counts.merge(p.theme(), 1, Integer::sum);
            if ("cafe".equals(p.theme())) {
                lastCafe = p.arriveMin();
            }
        }
        if (picks.isEmpty()) {
            return new Cursor(in.startMin(), null, -1, in.startLat(), in.startLng());
        }
        Pick tail = picks.get(picks.size() - 1);
        return new Cursor(tail.departMin(), tail.theme(), lastCafe, tail.place().lat(), tail.place().lng());
    }

    private Eligible rescueMeal(
            Context ctx, String window, List<Pick> picks, Set<String> used, Map<String, Integer> counts) {
        int windowStart = planner.mealWindowStart(window);
        int tolerance = settings.root().path("planning").path("mealRescueEarlyMin").asInt(30);
        List<Pick> backup = new ArrayList<>(picks);
        for (int pops = 0; pops < 2 && !picks.isEmpty(); pops++) {
            picks.remove(picks.size() - 1);
            stretchTail(picks, windowStart - 5);
            Cursor c = cursor(ctx.input, picks, used, counts);
            if ("food".equals(c.last())) {
                break;
            }
            Eligible found = choose(ctx, "food", false, c.lat(), c.lng(), c.clock(), picks, used, false);
            if (found != null && found.arriveMin() >= windowStart - tolerance) {
                return found;
            }
        }
        picks.clear();
        picks.addAll(backup);
        cursor(ctx.input, picks, used, counts);
        return null;
    }

    private void stretchTail(List<Pick> picks, int targetDepart) {
        if (picks.isEmpty()) {
            return;
        }
        Pick tail = picks.get(picks.size() - 1);
        int wanted = targetDepart - tail.arriveMin();
        int stay = Math.min(Math.max(tail.stay().maxMin(), tail.stayMin()), Math.max(tail.stayMin(), wanted));
        if (stay == tail.stayMin()) {
            return;
        }
        picks.set(picks.size() - 1, new Pick(tail.place(), tail.theme(), tail.travelMin(), tail.arriveMin(), stay,
                tail.stay(), tail.openStatus()));
    }

    private List<Pick> extendLast(List<Pick> picks, int endMin, int reserveMin) {
        if (picks.isEmpty()) {
            return picks;
        }
        Pick lastPick = picks.get(picks.size() - 1);
        int room = endMin - reserveMin - lastPick.departMin();
        int extension = Math.min(Math.max(room, 0), Math.max(lastPick.stay().maxMin() - lastPick.stayMin(), 0));
        if (extension <= 0) {
            return picks;
        }
        List<Pick> result = new ArrayList<>(picks);
        result.set(result.size() - 1, new Pick(lastPick.place(), lastPick.theme(), lastPick.travelMin(),
                lastPick.arriveMin(), lastPick.stayMin() + extension, lastPick.stay(), lastPick.openStatus()));
        return result;
    }

    private Eligible choose(
            Context ctx,
            String theme,
            boolean shortSlot,
            double lat,
            double lng,
            int clock,
            List<Pick> picks,
            Set<String> used,
            boolean mealPending) {
        JsonNode planning = settings.root().path("planning");
        int maxVerify = planning.path("maxVerifyPerSlot").asInt(20);
        int minEligible = collector.minEligible();
        Set<String> seen = new HashSet<>();
        List<Eligible> eligible = new ArrayList<>();
        int verifyCount = 0;
        int inner = 0;

        for (int step : collector.expansionSteps()) {
            List<KakaoPlace> raw = collector.collectRing(theme, lat, lng, inner, step);
            inner = step;
            List<Eligible> pre = new ArrayList<>();
            for (KakaoPlace p : raw) {
                if (!seen.add(p.id()) || used.contains(p.id())) {
                    continue;
                }
                Eligible e = prefilter(ctx, p, theme, shortSlot, clock, picks, mealPending);
                if (e != null) {
                    pre.add(e);
                }
            }
            for (Eligible e : rank(ctx, pre, theme)) {
                if (eligible.size() >= minEligible || verifyCount >= maxVerify) {
                    break;
                }
                int callsBefore = ctx.googleCalls;
                Eligible verified = verify(ctx, e, theme);
                if (ctx.googleCalls > callsBefore) {
                    verifyCount++;
                }
                if (verified != null) {
                    eligible.add(verified);
                }
            }
            if (eligible.size() >= minEligible) {
                break;
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        List<ScoredCandidate> scored = ctx.calculator.scoreAll(toCandidates(ctx, eligible, theme));
        String bestId = scored.get(0).candidate().kakaoId();
        for (Eligible e : eligible) {
            if (e.place().id().equals(bestId)) {
                return e;
            }
        }
        return eligible.get(0);
    }

    private List<Eligible> rank(Context ctx, List<Eligible> pre, String theme) {
        if (pre.size() < 2) {
            return pre;
        }
        List<ScoredCandidate> scored = ctx.calculator.scoreAll(toCandidates(ctx, pre, theme));
        Map<String, Eligible> byId = new HashMap<>();
        for (Eligible e : pre) {
            byId.put(e.place().id(), e);
        }
        List<Eligible> ordered = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            ordered.add(byId.get(sc.candidate().kakaoId()));
        }
        return ordered;
    }

    private List<Candidate> toCandidates(Context ctx, List<Eligible> list, String theme) {
        double themeMatch = ctx.input.themes().contains(theme) ? 1.0 : 0.6;
        List<Candidate> result = new ArrayList<>();
        for (Eligible e : list) {
            result.add(new Candidate(e.place().id(), e.place().distanceM(), e.rating(), e.reviews(), themeMatch, 0.0,
                    "confirmed".equals(e.openStatus()), 0.0));
        }
        return result;
    }

    private Eligible prefilter(
            Context ctx,
            KakaoPlace p,
            String theme,
            boolean shortSlot,
            int clock,
            List<Pick> picks,
            boolean mealPending) {
        Input in = ctx.input;
        if (isClosedDay(p.name(), in.date()) || isExcludedBar(p, in) || duplicatesChosen(p, theme, picks)) {
            return null;
        }
        int travel = walkTime.minutes(p.distanceM());
        int maxLeg = maxLegMinutes(in);
        if (maxLeg > 0 && travel > maxLeg) {
            return null;
        }
        int arrive = clock + travel;
        Stay stay = stayPolicy.stayFor(theme, p.name());
        int mealReserve = mealPending && !"food".equals(theme) ? mealNeedMin() : 0;
        int stayMin = shortSlot ? stay.minMin() : stay.defaultMin();
        if (arrive + stayMin + mealReserve + ctx.reserveMin > in.endMin()) {
            stayMin = stay.minMin();
        }
        if (arrive + stayMin + mealReserve + ctx.reserveMin > in.endMin()) {
            return null;
        }
        return new Eligible(p, travel, arrive, stayMin, stay, "unknown", null, null);
    }

    private int mealNeedMin() {
        int buffer = settings.root().path("planning").path("mealBufferMin").asInt(10);
        return stayPolicy.stayFor("food", "").minMin() + buffer;
    }

    private Eligible verify(Context ctx, Eligible e, String theme) {
        JsonNode planning = settings.root().path("planning");
        int maxCalls = planning.path("maxGoogleCallsPerRequest").asInt(30);
        boolean strict = planning.path("requireGoogleMatch").asBoolean(false);
        if (ctx.lookup == null) {
            return e;
        }
        if (ctx.googleCalls >= maxCalls) {
            return strict ? null : e;
        }
        LocalDate date = ctx.input.date();
        LocalDateTime arrive = date.atStartOfDay().plusMinutes(e.arriveMin());
        LocalDateTime depart = arrive.plusMinutes(e.stayMin());
        PlaceLookupService.Result result;
        try {
            result = ctx.lookup.lookup(e.place().id(), theme, date);
        } catch (RuntimeException ex) {
            return strict ? null : e;
        }
        if (result.google() != null) {
            ctx.googleCalls++;
        }
        String decision = result.decision();
        if ("EXCLUDE".equals(decision)) {
            return null;
        }
        if (!"CHECK_HOURS".equals(decision)) {
            return strict ? null : e;
        }
        PlaceLookupResponse response = PlaceLookupResponse.from(result, date, arrive, depart);
        if ("CLOSED".equals(response.openCheck())) {
            return null;
        }
        if (response.businessStatus() != null && !"OPERATIONAL".equals(response.businessStatus())) {
            return null;
        }
        String status = "OPEN".equals(response.openCheck()) ? "confirmed" : "unknown";
        return e.verified(status, response.rating(), response.userRatingCount());
    }

    private boolean isClosedDay(String name, LocalDate date) {
        for (JsonNode closed : settings.root().path("closedDays")) {
            String weekday = closed.path("weekday").asText();
            if (closed.path("name").asText().equals(name.strip())
                    && !weekday.isEmpty() && date.getDayOfWeek().name().startsWith(weekday)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcludedBar(KakaoPlace p, Input in) {
        boolean excludeBars = settings.root().path("companionRules").path(nullToEmpty(in.companion()))
                .path("excludeBars").asBoolean(false)
                || settings.root().path("ageGroupRules").path(nullToEmpty(in.ageGroup()))
                .path("excludeBars").asBoolean(false);
        if (!excludeBars) {
            return false;
        }
        JsonNode bar = settings.root().path("barRule");
        for (JsonNode c : bar.path("categoryContains")) {
            if (p.categoryName().contains(c.asText())) {
                return true;
            }
        }
        for (JsonNode k : bar.path("nameKeywords")) {
            if (p.name().contains(k.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean duplicatesChosen(KakaoPlace p, String theme, List<Pick> picks) {
        JsonNode rule = settings.root().path("duplicateRule");
        int within = rule.path("sameCategoryWithinMeters").asInt(300);
        for (Pick chosen : picks) {
            String chosenName = chosen.place().name().strip();
            if (rule.path("excludeIfNameStartsWithChosen").asBoolean(true) && p.name().strip().startsWith(chosenName)) {
                return true;
            }
            boolean sameKind = chosen.theme().equals(theme) && ("history".equals(theme) || "walk".equals(theme));
            if (sameKind && Geo.distanceM(p.lat(), p.lng(), chosen.place().lat(), chosen.place().lng()) < within) {
                return true;
            }
        }
        return false;
    }

    private int maxLegMinutes(Input in) {
        int family = settings.root().path("companionRules").path(nullToEmpty(in.companion())).path("maxLegMinutes").asInt(0);
        int mobility = settings.root().path("mobilityRules").path(nullToEmpty(in.mobility())).path("maxWalkLegMinutes").asInt(0);
        if (family > 0 && mobility > 0) {
            return Math.min(family, mobility);
        }
        return Math.max(family, mobility);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private ScoreCalculator calculator() {
        JsonNode w = settings.root().path("weights");
        ScoreWeights weights = new ScoreWeights(
                w.path("distance").asDouble(0.25),
                w.path("rating").asDouble(0.30),
                w.path("theme").asDouble(0.15),
                w.path("companion").asDouble(0.15),
                w.path("openVerified").asDouble(0.10),
                w.path("review").asDouble(0.05));
        if (!settings.root().path("reviewBonus").asBoolean(true)) {
            weights = weights.withoutReview();
        }
        double m = settings.root().path("rating").path("m").asDouble(50);
        List<Integer> steps = collector.expansionSteps();
        double maxDistance = steps.isEmpty() ? 1000 : steps.get(steps.size() - 1);
        return new ScoreCalculator(weights, new RatingCorrector(m), maxDistance);
    }
}
