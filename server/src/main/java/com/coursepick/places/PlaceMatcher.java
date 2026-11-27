package com.coursepick.places;

import com.coursepick.geo.Geo;
import com.coursepick.kakao.KakaoPlace;
import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceMatcher {

    public record Match(String status, String googlePlaceId, List<String> candidateIds, String reason) {
    }

    private record Ranked(double score, String id, boolean accepted, double similarity, double meters) {
    }

    private static final Pattern STREET = Pattern.compile("([가-힣0-9·]+(?:대로|로|길))\\s*(?:지하\\s*)?(\\d+(?:-\\d+)?)");

    private PlaceMatcher() {
    }

    public static Match classify(KakaoPlace kakao, List<JsonNode> candidates) {
        List<Ranked> ranked = new ArrayList<>();
        String kakaoName = normalized(kakao.name());
        String kakaoStreet = street(kakao.roadAddress());
        for (JsonNode place : candidates) {
            JsonNode location = place.path("location");
            String name = normalized(place.path("displayName").path("text").asText(""));
            String id = place.path("id").asText("");
            if (id.isEmpty() || name.isEmpty() || !location.path("latitude").isNumber()
                    || !location.path("longitude").isNumber()) {
                continue;
            }
            double meters = Geo.distanceM(
                    kakao.lat(), kakao.lng(), location.path("latitude").asDouble(), location.path("longitude").asDouble());
            double similarity = ratio(kakaoName, name);
            String candidateStreet = street(place.path("formattedAddress").asText(""));
            boolean sameAddress = kakaoStreet != null && kakaoStreet.equals(candidateStreet);
            boolean exact = kakaoName.equals(name);
            boolean accepted = (exact && (meters <= 40 || (meters <= 150 && sameAddress)))
                    || (similarity >= 0.88 && sameAddress && meters <= 100);
            double score = similarity * 0.7 + Math.max(0, 1 - meters / 300) * 0.2 + (sameAddress ? 0.1 : 0);
            ranked.add(new Ranked(score, id, accepted, similarity, meters));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed());
        if (ranked.isEmpty()) {
            return new Match("unmatched", null, List.of(), "no_candidates");
        }
        Ranked best = ranked.get(0);
        boolean ambiguous = ranked.size() > 1 && best.score() - ranked.get(1).score() < 0.08;
        if (best.accepted() && !ambiguous) {
            return new Match("matched", best.id(), List.of(), "runtime_match");
        }
        boolean plausible = ranked.stream().anyMatch(r -> r.similarity() >= 0.45 && r.meters() <= 500);
        List<String> ids = ranked.stream().map(Ranked::id).toList();
        return new Match(plausible ? "review" : "unmatched", null, ids,
                plausible ? "ambiguous_or_insufficient_evidence" : "no_plausible_candidate");
    }

    static String normalized(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase().replaceAll("[^a-z0-9가-힣]", "");
    }

    static String street(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = STREET.matcher(value);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1) + " " + matcher.group(2);
        }
        return found;
    }

    static double ratio(String a, String b) {
        int total = a.length() + b.length();
        if (total == 0) {
            return 1.0;
        }
        return 2.0 * matching(a, b) / total;
    }

    private static int matching(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int bestLen = 0;
        int bestA = 0;
        int bestB = 0;
        int[][] table = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    table[i][j] = table[i - 1][j - 1] + 1;
                    if (table[i][j] > bestLen) {
                        bestLen = table[i][j];
                        bestA = i - bestLen;
                        bestB = j - bestLen;
                    }
                }
            }
        }
        if (bestLen == 0) {
            return 0;
        }
        return bestLen
                + matching(a.substring(0, bestA), b.substring(0, bestB))
                + matching(a.substring(bestA + bestLen), b.substring(bestB + bestLen));
    }
}
