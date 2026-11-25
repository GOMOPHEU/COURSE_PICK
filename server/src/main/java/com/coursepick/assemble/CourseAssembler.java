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
        int searchCalls;
        final Map<String, Set<String>> seen = new HashMap<>();
        final Map<String, Map<String, String>> rejected = new HashMap<>();
        final Set<String> tried = new HashSet<>();

        void see(String theme, String id) {
            seen.computeIfAbsent(theme, k -> new HashSet<>()).add(id);
        }

        void reject(String theme, String id, String reason) {
            rejected.computeIfAbsent(theme, k -> new HashMap<>()).put(id, reason);
        }

        void accept(String theme, String id) {
            Map<String, String> map = rejected.get(theme);
            if (map != null) {
                map.remove(id);
            }
        }

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
        return assemble(in, Set.of());
    }

    public Optional<KakaoPlace> findPlace(String name) {
        return collector.findByName(name);
    }

    public List<Pick> assemble(Input in, Set<String> excludedIds) {
        return assemble(in, excludedIds, null);
    }

    public record Assembly(List<Pick> picks, Map<String, String> notes) {
    }

    public List<Pick> assemble(Input in, Set<String> excludedIds, KakaoPlace mustVisit) {
        return assembleWithNotes(in, excludedIds, mustVisit).picks();
    }

    public Assembly assembleWithNotes(Input in, Set<String> excludedIds, KakaoPlace mustVisit) {
        Context ctx = new Context(in, returnReserveMin(), lookupProvider.getIfAvailable(), calculator());
        List<Pick> picks = build(in, excludedIds, mustVisit, ctx);
        return new Assembly(picks, missingNotes(ctx, picks, in));
    }

    public static String themeLabel(String theme) {
        return switch (theme) {
            case "walk" -> "산책";
            case "culture" -> "문화";
            case "history" -> "역사";
            case "cafe" -> "카페";
            case "food" -> "식당";
            case "shopping" -> "쇼핑";
            default -> theme;
        };
    }

    private Map<String, String> missingNotes(Context ctx, List<Pick> picks, Input in) {
        Set<String> pickedThemes = new HashSet<>();
        Set<String> pickedIds = new HashSet<>();
        for (Pick p : picks) {
            pickedThemes.add(p.theme());
            pickedIds.add(p.place().id());
        }
        Map<String, String> notes = new java.util.LinkedHashMap<>();
        for (String theme : in.themes()) {
            if (pickedThemes.contains(theme) || notes.containsKey(theme)) {
                continue;
            }
            String label = themeLabel(theme);
            Set<String> seen = new HashSet<>(ctx.seen.getOrDefault(theme, Set.of()));
            seen.removeAll(pickedIds);
            if (seen.isEmpty()) {
                notes.put(theme, ctx.tried.contains(theme)
                        ? label + ": 이동 가능한 반경 안에 후보가 없어요"
                        : label + ": 시간이 부족해 일정에 넣지 못했어요");
                continue;
            }
            Map<String, Integer> counts = new HashMap<>();
            for (Map.Entry<String, String> e : ctx.rejected.getOrDefault(theme, Map.of()).entrySet()) {
                if (seen.contains(e.getKey())) {
                    counts.merge(e.getValue(), 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) {
                notes.put(theme, label + ": 후보 " + seen.size() + "곳이 있었지만 시간 배분상 넣지 못했어요");
                continue;
            }
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder text = new StringBuilder(label + ": 후보 " + seen.size() + "곳 중 ");
            for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(sorted.get(i).getKey()).append(' ').append(sorted.get(i).getValue()).append('곳');
            }
            notes.put(theme, text.toString());
        }
        return notes;
    }

    private List<Pick> build(Input in, Set<String> excludedIds, KakaoPlace mustVisit, Context ctx) {
        JsonNode planning = settings.root().path("planning");
        int maxPlaces = planning.path("maxPlaces").asInt(8);

        List<Pick> picks = new ArrayList<>();
        Set<String> used = new HashSet<>(excludedIds);
        Map<String, Integer> counts = new HashMap<>();
        Set<String> meals = new HashSet<>();
        int clock = in.startMin();
        String last = null;
        int lastCafe = -1;
        double lat = in.startLat();
        double lng = in.startLng();

        String mustTheme = null;
        boolean mustDone = mustVisit == null;
        if (mustVisit != null) {
            mustTheme = collector.themeOf(mustVisit, in.themes());
            if (mustTheme == null) {
                mustTheme = "walk";
            }
        }

        while (picks.size() < maxPlaces) {
            PlanState state = new PlanState(
                    in.startMin(), clock, in.endMin(), last, lastCafe, Map.copyOf(counts), Set.copyOf(meals), in.themes());
            String pending = planner.pendingMealWindow(state);
            boolean mealPending = pending != null;
            Slot slot = planner.next(state).orElse(null);
            if (slot == null && mealPending && !"food".equals(last) && !mealTooEarly(pending, clock)) {
                slot = new Slot("food", false, pending);
            }
            if (slot == null) {
                break;
            }
            if (slot.mealWindow() != null) {
                meals.add(slot.mealWindow());
            }
            String theme = slot.theme();
            Eligible chosen = null;
            if (!mustDone) {
                boolean now = "food".equals(mustTheme)
                        ? "food".equals(theme) : slot.mealWindow() == null && !mustTheme.equals(last);
                if (now) {
                    mustDone = true;
                    chosen = mustEligible(ctx, mustVisit, mustTheme, lat, lng, clock, picks, mealPending);
                    if (chosen != null) {
                        theme = mustTheme;
                    }
                }
            }
            if (chosen == null) {
                chosen = choose(ctx, theme, slot.shortSlot(), lat, lng, clock, picks, used, mealPending);
            }
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
                List<String> alts = new ArrayList<>(in.themes());
                for (String extra : slot.shortSlot() ? List.of("walk") : List.of("walk", "culture", "history", "cafe")) {
                    if (!alts.contains(extra)) {
                        alts.add(extra);
                    }
                }
                for (String alt : alts) {
                    if (alt.equals(slot.theme()) || alt.equals(last) || !planner.allows(state, alt)) {
                        continue;
                    }
                    chosen = choose(ctx, alt, slot.shortSlot(), lat, lng, clock, picks, used, mealPending);
                    if (chosen != null) {
                        theme = alt;
                        break;
                    }
                }
            }
            if (chosen == null && mealPending && !"food".equals(last) && !mealTooEarly(pending, clock)) {
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
        Set<String> servedMeals = new HashSet<>();
        for (String window : List.of("lunch", "dinner")) {
            int windowStart = planner.mealWindowStart(window);
            for (Pick p : picks) {
                if ("food".equals(p.theme()) && p.arriveMin() >= windowStart - 90) {
                    servedMeals.add(window);
                }
            }
        }
        PlanState finalState = new PlanState(
                in.startMin(), clock, in.endMin(), last, lastCafe, Map.copyOf(counts), servedMeals, in.themes());
        String missingMeal = planner.pendingMealWindow(finalState);
        if (missingMeal != null) {
            Eligible rescued = rescueMeal(ctx, missingMeal, picks, used, counts);
            if (rescued == null) {
                return picks;
            }
            picks.add(new Pick(rescued.place(), "food", rescued.travelMin(), rescued.arriveMin(), rescued.stayMin(),
                    rescued.stay(), rescued.openStatus()));
        }
        return extendLast(picks, in.endMin(), ctx.reserveMin);
    }

    private boolean mealTooEarly(String window, int clock) {
        int early = settings.root().path("planning").path("forcedMealEarlyMin").asInt(60);
        return clock < planner.mealWindowStart(window) - early;
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

    private List<Eligible> collectEligible(
            Context ctx,
            String theme,
            boolean shortSlot,
            double lat,
            double lng,
            int clock,
            List<Pick> picks,
            Set<String> used,
            boolean mealPending,
            int minEligible) {
        JsonNode planning = settings.root().path("planning");
        int maxVerify = planning.path("maxVerifyPerSlot").asInt(20);
        Set<String> seen = new HashSet<>();
        List<Eligible> eligible = new ArrayList<>();
        int verifyCount = 0;
        int inner = 0;
        ctx.tried.add(theme);

        for (int step : collector.expansionSteps()) {
            List<KakaoPlace> raw = collector.collectRing(theme, lat, lng, inner, step);
            inner = step;
            List<Eligible> pre = new ArrayList<>();
            for (KakaoPlace p : raw) {
                if (!seen.add(p.id())) {
                    continue;
                }
                ctx.see(theme, p.id());
                if (used.contains(p.id())) {
                    ctx.reject(theme, p.id(), "앞선 코스에서 쓴 곳");
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
                int callsBefore = ctx.googleCalls + ctx.searchCalls;
                Eligible verified = verify(ctx, e, theme);
                if (ctx.googleCalls + ctx.searchCalls > callsBefore) {
                    verifyCount++;
                }
                if (verified != null) {
                    ctx.accept(theme, e.place().id());
                    eligible.add(verified);
                }
            }
            if (eligible.size() >= minEligible) {
                break;
            }
        }
        return eligible;
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
        List<Eligible> eligible = collectEligible(
                ctx, theme, shortSlot, lat, lng, clock, picks, used, mealPending, collector.minEligible());
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
        if (isClosedDay(p.name(), in.date())) {
            ctx.reject(theme, p.id(), "휴관일");
            return null;
        }
        if (isExcludedBar(p, in)) {
            ctx.reject(theme, p.id(), "주류 업종 제외");
            return null;
        }
        if (duplicatesChosen(p, theme, picks)) {
            ctx.reject(theme, p.id(), "이미 고른 곳과 중복");
            return null;
        }
        int travel = walkTime.minutes(p.distanceM());
        int maxLeg = maxLegMinutes(in);
        if (maxLeg > 0 && travel > maxLeg) {
            ctx.reject(theme, p.id(), "이동 시간 한도 초과");
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
            ctx.reject(theme, p.id(), "남은 시간 부족");
            return null;
        }
        return new Eligible(p, travel, arrive, stayMin, stay, "unknown", null, null);
    }

    private int mealNeedMin() {
        int buffer = settings.root().path("planning").path("mealBufferMin").asInt(10);
        return stayPolicy.stayFor("food", "").minMin() + buffer;
    }

    private Eligible mustEligible(
            Context ctx, KakaoPlace place, String theme, double lat, double lng, int clock, List<Pick> picks,
            boolean mealPending) {
        int distance = (int) Math.round(Geo.distanceM(lat, lng, place.lat(), place.lng()));
        KakaoPlace anchored = new KakaoPlace(place.id(), place.name(), place.categoryName(),
                place.categoryGroupCode(), place.address(), place.roadAddress(), place.lat(), place.lng(),
                distance, place.placeUrl());
        Eligible e = prefilter(ctx, anchored, theme, false, clock, picks, mealPending);
        if (e == null) {
            return null;
        }
        return verify(ctx, e, theme, true);
    }

    private Eligible verify(Context ctx, Eligible e, String theme) {
        return verify(ctx, e, theme, false);
    }

    private Eligible verify(Context ctx, Eligible e, String theme, boolean lenient) {
        JsonNode planning = settings.root().path("planning");
        int maxCalls = planning.path("maxGoogleCallsPerRequest").asInt(30);
        boolean strict = !lenient && planning.path("requireGoogleMatch").asBoolean(false);
        if (ctx.lookup == null) {
            return e;
        }
        int maxSearches = planning.path("maxGoogleSearchesPerRequest").asInt(60);
        String id = e.place().id();
        if (ctx.googleCalls >= maxCalls || ctx.searchCalls >= maxSearches) {
            if (strict) {
                ctx.reject(theme, id, "구글 확인 한도 초과");
                return null;
            }
            return e;
        }
        LocalDate date = ctx.input.date();
        LocalDateTime arrive = date.atStartOfDay().plusMinutes(e.arriveMin());
        LocalDateTime depart = arrive.plusMinutes(e.stayMin());
        PlaceLookupService.Result result;
        int searchesBefore = ctx.lookup.searchCount();
        try {
            result = ctx.lookup.lookup(e.place(), theme, date);
        } catch (RuntimeException ex) {
            ctx.searchCalls += ctx.lookup.searchCount() - searchesBefore;
            if (strict) {
                ctx.reject(theme, id, "구글 조회 실패");
                return null;
            }
            return e;
        }
        ctx.searchCalls += ctx.lookup.searchCount() - searchesBefore;
        if (result.google() != null) {
            ctx.googleCalls++;
        }
        String decision = result.decision();
        if ("EXCLUDE".equals(decision)) {
            ctx.reject(theme, id, "제외 대상 장소");
            return null;
        }
        if (!"CHECK_HOURS".equals(decision)) {
            if (strict) {
                ctx.reject(theme, id, "구글 매칭 실패");
                return null;
            }
            return e;
        }
        PlaceLookupResponse response = PlaceLookupResponse.from(result, date, arrive, depart);
        if ("CLOSED".equals(response.openCheck())) {
            ctx.reject(theme, id, "방문 시각에 영업 안 함");
            return null;
        }
        if (response.businessStatus() != null && !"OPERATIONAL".equals(response.businessStatus())) {
            ctx.reject(theme, id, "폐업 또는 휴업");
            return null;
        }
        if (!"OPEN".equals(response.openCheck()) && !lenient
                && planning.path("requireKnownHours").asBoolean(false)) {
            ctx.reject(theme, id, "영업시간 확인 불가");
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
