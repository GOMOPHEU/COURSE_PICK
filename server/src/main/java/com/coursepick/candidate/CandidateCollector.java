package com.coursepick.candidate;

import com.coursepick.config.CoursepickSettings;
import com.coursepick.geo.Geo;
import com.coursepick.geo.Rect;
import com.coursepick.kakao.KakaoLocalClient;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.kakao.KakaoSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class CandidateCollector {

    private final KakaoLocalClient kakao;
    private final CoursepickSettings settings;

    public CandidateCollector(KakaoLocalClient kakao, CoursepickSettings settings) {
        this.kakao = kakao;
        this.settings = settings;
    }

    private JsonNode search() {
        return settings.root().path("candidateSearch");
    }

    public int minEligible() {
        return search().path("minEligible").asInt(3);
    }

    public List<Integer> expansionSteps() {
        List<Integer> steps = new ArrayList<>();
        JsonNode configured = search().path("stepsM");
        if (configured.isArray() && !configured.isEmpty()) {
            for (JsonNode n : configured) {
                steps.add(n.asInt());
            }
            return steps;
        }
        int start = search().path("startHalfSizeM").asInt(100);
        int step = Math.max(search().path("stepM").asInt(100), 1);
        int max = search().path("maxHalfSizeM").asInt(1000);
        for (int size = start; size <= max; size += step) {
            steps.add(size);
        }
        return steps;
    }

    public List<KakaoPlace> collectRing(String theme, double lat, double lng, int innerHalfM, int outerHalfM) {
        JsonNode rule = search().path("themes").path(theme);
        if (rule.isMissingNode()) {
            throw new IllegalArgumentException("설정에 없는 테마입니다: " + theme);
        }
        int pages = search().path("pages").asInt(2);
        int maxDepth = search().path("maxSplitDepth").asInt(2);
        int[] budget = {search().path("maxRequestsPerStep").asInt(30)};

        List<Function<Rect, KakaoSearchResult>> queries = new ArrayList<>();
        for (JsonNode code : rule.path("categoryCodes")) {
            queries.add(rect -> kakao.searchCategoryRect(code.asText(), rect, pages));
        }
        for (JsonNode keyword : rule.path("keywords")) {
            queries.add(rect -> kakao.searchKeywordRect(keyword.asText(), rect, pages));
        }

        Map<String, KakaoPlace> unique = new LinkedHashMap<>();
        int pageLimit = pages * KakaoLocalClient.PAGE_SIZE;
        for (Rect rect : Rect.ring(lat, lng, innerHalfM, outerHalfM)) {
            for (Function<Rect, KakaoSearchResult> query : queries) {
                run(query, rect, 0, maxDepth, pageLimit, budget, unique);
            }
        }
        return filter(rule, unique, lat, lng);
    }

    private void run(
            Function<Rect, KakaoSearchResult> query,
            Rect rect,
            int depth,
            int maxDepth,
            int pageLimit,
            int[] budget,
            Map<String, KakaoPlace> unique) {
        if (budget[0] <= 0) {
            return;
        }
        budget[0]--;
        KakaoSearchResult result = query.apply(rect);
        for (KakaoPlace p : result.places()) {
            unique.putIfAbsent(p.id(), p);
        }
        if (result.totalCount() > pageLimit && depth < maxDepth) {
            for (Rect quarter : rect.quarters()) {
                run(query, quarter, depth + 1, maxDepth, pageLimit, budget, unique);
            }
        }
    }

    private List<KakaoPlace> filter(JsonNode rule, Map<String, KakaoPlace> unique, double lat, double lng) {
        List<String> allowed = strings(rule.path("allowedCategoryPrefixes"));
        List<String> excludedPrefixes = strings(rule.path("excludeCategoryPrefixes"));
        List<String> excludedNames = strings(rule.path("excludeNameKeywords"));
        double regionLat = settings.regionLat();
        double regionLng = settings.regionLng();
        int regionRadius = settings.regionRadiusM();

        List<KakaoPlace> result = new ArrayList<>();
        for (KakaoPlace p : unique.values()) {
            if (Geo.distanceM(regionLat, regionLng, p.lat(), p.lng()) > regionRadius) {
                continue;
            }
            String category = p.categoryName().replaceAll("\\s*>\\s*", " > ").strip();
            if (!allowed.isEmpty() && allowed.stream().noneMatch(category::startsWith)) {
                continue;
            }
            if (excludedPrefixes.stream().anyMatch(category::startsWith)) {
                continue;
            }
            if (excludedNames.stream().anyMatch(p.name()::contains)) {
                continue;
            }
            int distance = (int) Math.round(Geo.distanceM(lat, lng, p.lat(), p.lng()));
            result.add(new KakaoPlace(p.id(), p.name(), category, p.categoryGroupCode(), p.address(),
                    p.roadAddress(), p.lat(), p.lng(), distance, p.placeUrl()));
        }
        if (rule.path("collapseNamePrefix").asBoolean(false)) {
            result = collapseNamePrefix(result);
        }
        result.sort(Comparator.comparingInt(KakaoPlace::distanceM));
        return List.copyOf(result);
    }

    private List<KakaoPlace> collapseNamePrefix(List<KakaoPlace> places) {
        List<KakaoPlace> byNameLength = new ArrayList<>(places);
        byNameLength.sort(Comparator.comparingInt(p -> p.name().strip().length()));
        List<KakaoPlace> kept = new ArrayList<>();
        for (KakaoPlace p : byNameLength) {
            String name = p.name().strip();
            boolean child = kept.stream().anyMatch(k -> {
                String keptName = k.name().strip();
                return name.length() > keptName.length() && name.startsWith(keptName);
            });
            if (!child) {
                kept.add(p);
            }
        }
        return kept;
    }

    private List<String> strings(JsonNode array) {
        List<String> list = new ArrayList<>();
        for (JsonNode n : array) {
            list.add(n.asText());
        }
        return list;
    }
}
