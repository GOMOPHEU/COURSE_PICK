package com.coursepick.candidate;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CandidateService {
    private final KakaoCandidateClient client;
    private final CandidatePolicy policy;
    public CandidateService(KakaoCandidateClient client, CandidatePolicy policy) {
        this.client = client;
        this.policy = policy;
    }
    public record Candidate(String kakaoId, String name, String theme, String category,
                            double lat, double lng, String address, String placeUrl,
                            String groupKey, long distanceMeters, String openStatus) {}
    public record Result(List<Candidate> candidates, int radiusMeters, boolean expanded,
                         boolean insufficient, String eligibilityStage) {}

    public Result find(CandidateRequest request) {
        Map<String, KakaoPlace> collected = new LinkedHashMap<>();
        int radius = policy.initialRadius();
        while (true) {
            for (CandidatePolicy.Search search : policy.searches(request.theme())) {
                for (int page = 1; page <= policy.maxPages(); page++) {
                    var result = client.search(search, request.lat(), request.lng(), radius, page);
                    for (KakaoPlace place : result.places()) collected.putIfAbsent(place.id(), place);
                    if (result.end()) break;
                }
            }
            List<Candidate> candidates = filter(request, collected.values(), radius);
            if (candidates.size() >= policy.minCandidates() || radius >= policy.maxRadius())
                return new Result(candidates, radius, radius > policy.initialRadius(), candidates.size() < policy.minCandidates(), "category_and_companion");
            radius = policy.nextRadius(radius);
        }
    }

    List<Candidate> filter(CandidateRequest request, Collection<KakaoPlace> places, int radius) {
        Map<String, KakaoPlace> groups = new LinkedHashMap<>();
        // Parent palace is preferred only when it was actually returned and is eligible.
        places.stream().filter(p -> !request.ids().contains(p.id()) && !request.groups().contains(policy.groupKey(p)))
                .filter(policy::inRegion)
                .filter(p -> policy.themeMatches(request.theme(), p) && policy.companionAllows(request, p))
                .filter(p -> distance(request, p) <= radius)
                .sorted(Comparator.comparing((KakaoPlace p) -> !policy.isPalaceParent(p))
                        .thenComparingDouble(p -> distance(request, p)).thenComparing(KakaoPlace::id))
                .forEach(p -> groups.putIfAbsent(policy.groupKey(p), p));
        return groups.values().stream().sorted(Comparator.comparingDouble((KakaoPlace p) -> distance(request, p)).thenComparing(KakaoPlace::id))
                .map(p -> new Candidate(p.id(), p.name(), request.theme(), p.category(), p.lat(), p.lng(), p.address(), p.url(),
                        policy.groupKey(p), Math.round(distance(request, p)), "unknown")).toList();
    }
    private double distance(CandidateRequest r, KakaoPlace p) {
        return CandidatePolicy.distance(r.lat(), r.lng(), p.lat(), p.lng());
    }
}
