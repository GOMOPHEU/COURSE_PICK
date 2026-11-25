package com.coursepick.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import com.coursepick.kakao.KakaoPlace;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/** Reusable from a Spring @Bean; this module intentionally doesn't create a server. */
public final class PlaceLookupService {
    public record Result(String kakaoId, String decision, String source, String reason,
                         GooglePlacesClient.Details google, JsonNode official) {}
    private final GooglePlacesClient google;
    private final Map<String, JsonNode> mappings = new HashMap<>();
    private final Map<String, JsonNode> official = new HashMap<>();
    private final Map<String, JsonNode> runtime = new ConcurrentHashMap<>();
    private final ObjectMapper writer = new ObjectMapper();
    private Path runtimePath;
    private int dailySearchLimit;
    private boolean runtimeMatching;
    private LocalDate searchDay;
    private int searchToday;
    private int searchTotal;

    public PlaceLookupService(GooglePlacesClient google, Path mappingsFile, Path officialFile) throws IOException {
        this.google = google;
        ObjectMapper json = new ObjectMapper();
        JsonNode mappingData = json.readTree(mappingsFile.toFile());
        JsonNode officialData = json.readTree(officialFile.toFile());
        if (mappingData.path("schemaVersion").asInt() != 1 || !mappingData.path("mappings").isArray()
            || officialData.path("schemaVersion").asInt() != 1 || !officialData.path("facilities").isArray())
            throw new IOException("Unsupported place-data schema");
        for (JsonNode row : mappingData.get("mappings")) {
            String id = row.path("kakaoId").asText();
            if (id.isBlank() || mappings.putIfAbsent(id, row) != null)
                throw new IOException("Invalid or duplicate Kakao ID");
        }
        for (JsonNode row : officialData.get("facilities")) {
            String id = row.path("kakaoId").asText();
            if (id.isBlank() || official.putIfAbsent(id, row) != null)
                throw new IOException("Invalid or duplicate official facility ID");
            if (!row.path("sources").isArray() || (row.path("verificationStatus").asText().equals("verified") && row.path("sources").isEmpty()))
                throw new IOException("Official source required");
            for (JsonNode source : row.get("sources")) {
                if (!source.path("url").asText().startsWith("https://")) throw new IOException("Invalid official URL");
                try { LocalDate.parse(source.path("checkedAt").asText()); }
                catch (RuntimeException e) { throw new IOException("Invalid source check date"); }
            }
        }
    }

    public void enableRuntimeMatching(Path cacheFile, int dailyLimit) throws IOException {
        this.runtimePath = cacheFile;
        this.dailySearchLimit = dailyLimit;
        this.runtimeMatching = true;
        if (cacheFile != null && Files.exists(cacheFile)) {
            JsonNode data = writer.readTree(cacheFile.toFile());
            for (JsonNode row : data.path("mappings")) {
                String id = row.path("kakaoId").asText();
                if (!id.isBlank()) runtime.put(id, row);
            }
        }
    }

    public synchronized int searchCount() {
        return searchTotal;
    }

    private static void validate(String kakaoId, String theme, LocalDate visitDate) {
        if (kakaoId == null || kakaoId.isBlank() || visitDate == null || theme == null
            || !Set.of("cafe", "food", "walk", "shopping", "history", "culture").contains(theme))
            throw new IllegalArgumentException("Kakao ID, valid theme and visitDate required");
    }

    public Result lookup(KakaoPlace place, String theme, LocalDate visitDate) {
        validate(place.id(), theme, visitDate);
        JsonNode mapping = mappings.get(place.id());
        if (mapping == null) {
            JsonNode known = official.get(place.id());
            boolean officialVerified = known != null && known.path("verificationStatus").asText().equals("verified")
                && known.hasNonNull("hours");
            if (!officialVerified && runtimeMatching) mapping = resolveRuntime(place);
        }
        return evaluate(place.id(), theme, visitDate, mapping);
    }

    private JsonNode resolveRuntime(KakaoPlace place) {
        JsonNode cached = runtime.get(place.id());
        if (cached != null) return cached;
        if (!takeSearchSlot()) return statusRow(place.id(), "api_error", null, List.of(), "SEARCH_LIMIT");
        String address = place.roadAddress() == null || place.roadAddress().isBlank() ? place.address() : place.roadAddress();
        Optional<List<JsonNode>> found = google.searchText(place.name() + " " + address, place.lat(), place.lng());
        if (found.isEmpty()) return statusRow(place.id(), "api_error", null, List.of(), "SEARCH_FAILED");
        PlaceMatcher.Match match = PlaceMatcher.classify(place, found.get());
        JsonNode row = statusRow(place.id(), match.status(), match.googlePlaceId(), match.candidateIds(), match.reason());
        runtime.put(place.id(), row);
        persist();
        return row;
    }

    private synchronized boolean takeSearchSlot() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!today.equals(searchDay)) {
            searchDay = today;
            searchToday = 0;
        }
        if (searchToday >= dailySearchLimit) return false;
        searchToday++;
        searchTotal++;
        return true;
    }

    private JsonNode statusRow(String id, String status, String googlePlaceId, List<String> candidates, String reason) {
        ObjectNode row = writer.createObjectNode();
        row.put("kakaoId", id);
        row.put("status", status);
        if (googlePlaceId != null) row.put("googlePlaceId", googlePlaceId);
        var ids = row.putArray("candidatePlaceIds");
        candidates.forEach(ids::add);
        row.put("reason", reason);
        row.put("checkedAt", LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        return row;
    }

    private synchronized void persist() {
        if (runtimePath == null) return;
        try {
            ObjectNode root = writer.createObjectNode();
            root.put("schemaVersion", 1);
            var rows = root.putArray("mappings");
            new ArrayList<>(runtime.values()).forEach(rows::add);
            Path parent = runtimePath.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = runtimePath.resolveSibling(runtimePath.getFileName() + ".tmp");
            Files.writeString(temp, writer.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            Files.move(temp, runtimePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /** Decision only concerns data eligibility, NOT opening at a planned arrival time. */
    public Result lookup(String kakaoId, String theme, LocalDate visitDate) {
        validate(kakaoId, theme, visitDate);
        return evaluate(kakaoId, theme, visitDate, mappings.get(kakaoId));
    }

    private Result evaluate(String kakaoId, String theme, LocalDate visitDate, JsonNode mapping) {
        if (mapping == null) {
            JsonNode known = official.get(kakaoId);
            if (known != null && known.path("verificationStatus").asText().equals("verified") && known.hasNonNull("hours"))
                return result(kakaoId, "CHECK_HOURS", "official", "OFFICIAL_RULES_REQUIRE_EVALUATION", null, known);
            return result(kakaoId, "KEEP_UNKNOWN", "unknown", "MAPPING_NOT_BUILT", null, null);
        }
        String status = mapping.path("status").asText();
        JsonNode verified = official.get(kakaoId);
        if ((status.equals("review") || status.equals("api_error")) && verified != null
            && verified.path("verificationStatus").asText().equals("verified") && verified.hasNonNull("hours"))
            return result(kakaoId, "CHECK_HOURS", "official", "OFFICIAL_RULES_REQUIRE_EVALUATION", null, verified);
        if (status.equals("api_error")) return result(kakaoId, "KEEP_UNKNOWN", "unknown", "MATCH_API_ERROR", null, null);
        if (status.equals("review")) return result(kakaoId, "NEEDS_REVIEW", "unknown", "MATCH_REVIEW_REQUIRED", null, null);
        if (status.equals("matched")) {
            GooglePlacesClient.Details details = google.fetch(mapping.path("googlePlaceId").asText());
            if (details.state() == GooglePlacesClient.State.API_ERROR)
                return result(kakaoId, "KEEP_UNKNOWN", "unknown", details.errorCode(), details, null);
            if (details.state() == GooglePlacesClient.State.NOT_FOUND)
                return result(kakaoId, "NEEDS_REVIEW", "unknown", "STALE_PLACE_ID", details, null);
            if (Set.of("CLOSED_PERMANENTLY", "CLOSED_TEMPORARILY").contains(details.businessStatus()))
                return result(kakaoId, "EXCLUDE", "google", details.businessStatus(), details, null);
            // Caller compares periods against arrival AND departure, including midnight/breaks.
            return result(kakaoId, "CHECK_HOURS", "google", details.hoursBasis(visitDate).name(), details, null);
        }
        if (!status.equals("unmatched"))
            return result(kakaoId, "KEEP_UNKNOWN", "unknown", "UNKNOWN_MATCH_STATUS", null, null);
        if (theme.equals("food") || theme.equals("cafe"))
            return result(kakaoId, "EXCLUDE", "unknown", "UNMATCHED_FOOD_OR_CAFE", null, null);
        JsonNode facility = official.get(kakaoId);
        if (facility != null && facility.path("verificationStatus").asText().equals("verified")
            && facility.hasNonNull("hours"))
            return result(kakaoId, "CHECK_HOURS", "official", "OFFICIAL_RULES_REQUIRE_EVALUATION", null, facility);
        return result(kakaoId, "KEEP_UNKNOWN", "unknown", "OFFICIAL_HOURS_UNCONFIRMED", null, facility);
    }
    private static Result result(String id, String decision, String source, String reason,
                                 GooglePlacesClient.Details google, JsonNode official) {
        return new Result(id, decision, source, reason, google, official);
    }
}
