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
import java.util.Optional;
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
            Optional<Slot> next = planner.next(state);
            if (next.isEmpty()) {
                break;
            }
            Slot slot = next.get();
            if (slot.mealWindow() != null) {
                meals.add(slot.mealWindow());
            }
            String theme = slot.theme();
            Eligible chosen = choose(ctx, theme, slot.shortSlot(), lat, lng, clock, picks, used);
            if (chosen == null && slot.mealWindow() != null) {
                continue;
            }
            if (chosen == null) {
                for (String alt : in.themes()) {
                    if (alt.equals(slot.theme()) || alt.equals(last)) {
                        continue;
                    }
                    chosen = choose(ctx, alt, slot.shortSlot(), lat, lng, clock, picks, used);
                    if (chosen != null) {
                        theme = alt;
                        break;
                    }
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
        }
        return extendLast(picks, in.endMin(), ctx.reserveMin);
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
            Set<String> used) {
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
                Eligible e = prefilter(ctx, p, theme, shortSlot, clock, picks);
                if (e != null) {
                    pre.add(e);
                }
            }
            for (Eligible e : rank(ctx, pre, theme)) {
                if (eligible.size() >= minEligible || verifyCount >= maxVerify) {
                    break;
                }
                verifyCount++;
                Eligible verified = verify(ctx, e, theme);
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
            Context ctx, KakaoPlace p, String theme, boolean shortSlot, int clock, List<Pick> picks) {
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
        int stayMin = shortSlot ? stay.minMin() : stay.defaultMin();
        if (arrive + stayMin + ctx.reserveMin > in.endMin()) {
            stayMin = stay.minMin();
        }
        if (arrive + stayMin + ctx.reserveMin > in.endMin()) {
            return null;
        }
        return new Eligible(p, travel, arrive, stayMin, stay, "unknown", null, null);
    }

    private Eligible verify(Context ctx, Eligible e, String theme) {
        int maxCalls = settings.root().path("planning").path("maxGoogleCallsPerRequest").asInt(30);
        if (ctx.lookup == null || ctx.googleCalls >= maxCalls) {
            return e;
        }
        ctx.googleCalls++;
        LocalDate date = ctx.input.date();
        LocalDateTime arrive = date.atStartOfDay().plusMinutes(e.arriveMin());
        LocalDateTime depart = arrive.plusMinutes(e.stayMin());
        PlaceLookupService.Result result;
        try {
            result = ctx.lookup.lookup(e.place().id(), theme, date);
        } catch (RuntimeException ex) {
            return e;
        }
        String decision = result.decision();
        if ("EXCLUDE".equals(decision)) {
            return null;
        }
        if (!"CHECK_HOURS".equals(decision)) {
            return e;
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
