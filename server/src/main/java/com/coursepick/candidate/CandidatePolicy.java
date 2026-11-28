package com.coursepick.candidate;

import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CandidatePolicy {
    private final JsonNode config;
    private final JsonNode region;
    public CandidatePolicy(CoursepickSettings settings) {
        config = settings.root().path("candidateSearch");
        region = settings.root().path("region");
    }
    public int initialRadius() { return config.path("radiusMeters").asInt(690); }
    public int maxRadius() { return config.path("maxRadiusMeters").asInt(865); }
    public int minCandidates() { return config.path("minCandidates").asInt(3); }
    public int maxPages() { return Math.min(3, Math.max(1, config.path("maxPages").asInt(2))); }
    public int nextRadius(int radius) { return Math.min(maxRadius(), Math.max(radius + 1, (int) Math.ceil(radius * 1.5))); }
    public record Search(String groupCode, String keyword) {}
    public List<Search> searches(String theme) {
        return switch (theme) {
            case "food" -> List.of(new Search("FD6", null));
            case "cafe" -> List.of(new Search("CE7", null));
            case "walk", "history" -> List.of(new Search("AT4", null));
            case "exhibition", "culture" -> List.of(new Search("CT1", null));
            case "shopping" -> {
                List<Search> searches = new ArrayList<>();
                searches.add(new Search("AT4", null));
                for (JsonNode keyword : config.path("shoppingKeywords")) searches.add(new Search(null, keyword.asText()));
                yield searches;
            }
            default -> throw new IllegalArgumentException("Unknown theme");
        };
    }
    private List<String> segments(KakaoPlace p) { return Arrays.stream(p.category().split(">")).map(String::trim).toList(); }
    public boolean themeMatches(String theme, KakaoPlace p) {
        for (JsonNode id : config.path("excludedPlaceIds")) if (p.id().equals(id.asText())) return false;
        List<String> parts = segments(p);
        return switch (theme) {
            case "food" -> p.groupCode().equals("FD6") && Collections.disjoint(parts, List.of("간식", "구내식당", "도시락"));
            case "cafe" -> p.groupCode().equals("CE7") && parts.size() >= 2 && parts.get(0).equals("음식점") && parts.get(1).equals("카페");
            case "walk" -> p.groupCode().equals("AT4") && !parts.contains("도보여행");
            case "history" -> p.groupCode().equals("AT4") && !Collections.disjoint(parts, List.of("문화유적", "고궁"));
            case "exhibition", "culture" -> p.groupCode().equals("CT1");
            case "shopping" -> shopping(p, parts);
            default -> false;
        };
    }
    private boolean shopping(KakaoPlace p, List<String> parts) {
        if (!Collections.disjoint(parts, List.of("인터넷쇼핑몰", "통신판매", "백화점", "편의점", "보석,귀금속", "화장품"))) return false;
        if (p.groupCode().equals("AT4")) return parts.contains("테마거리");
        if (parts.isEmpty() || !parts.get(0).equals("가정,생활")) return false;
        for (JsonNode allowed : config.path("shoppingAllowedCategories")) if (parts.contains(allowed.asText())) return true;
        return false;
    }
    public boolean companionAllows(CandidateRequest request, KakaoPlace place) {
        boolean excludeBars = "family".equals(request.companion()) || "10s".equals(request.ageGroup()) || Boolean.TRUE.equals(request.withChildren());
        if (!excludeBars) return true;
        if (segments(place).contains("술집")) return false;
        for (JsonNode keyword : config.path("barNameKeywords")) if (place.name().contains(keyword.asText())) return false;
        return true;
    }
    /** Only explicit palace prefixes and known children are grouped; nearby independent POIs stay separate. */
    public String groupKey(KakaoPlace p) {
        if (p.groupCode().equals("AT4") && !Collections.disjoint(segments(p), List.of("문화유적", "고궁"))) {
            for (JsonNode palace : config.path("palaceGroups")) {
                String name = palace.path("name").asText();
                boolean matches = p.name().equals(name) || p.name().startsWith(name + " ");
                for (JsonNode child : palace.path("children")) matches |= p.name().equals(child.asText());
                if (matches && distance(p.lat(), p.lng(), palace.path("lat").asDouble(), palace.path("lng").asDouble()) <= palace.path("radiusMeters").asInt())
                    return palace.path("key").asText();
            }
        }
        return "kakao:" + p.id();
    }
    public boolean isPalaceParent(KakaoPlace p) {
        for (JsonNode palace : config.path("palaceGroups")) if (p.name().equals(palace.path("name").asText())) return true;
        return false;
    }
    public boolean inRegion(KakaoPlace p) {
        return distance(p.lat(), p.lng(), region.path("lat").asDouble(), region.path("lng").asDouble()) <= region.path("radiusM").asInt();
    }
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(Math.toRadians(lng2 - lng1) / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }
}
