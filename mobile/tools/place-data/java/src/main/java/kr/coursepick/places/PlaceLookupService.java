package kr.coursepick.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Reusable from a Spring @Bean; this module intentionally doesn't create a server. */
public final class PlaceLookupService {
    public record Result(String kakaoId, String decision, String source, String reason,
                         GooglePlacesClient.Details google, JsonNode official) {}
    private final GooglePlacesClient google;
    private final Map<String, JsonNode> mappings = new HashMap<>();
    private final Map<String, JsonNode> official = new HashMap<>();

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

    /** Decision only concerns data eligibility, NOT opening at a planned arrival time. */
    public Result lookup(String kakaoId, String theme, LocalDate visitDate) {
        if (kakaoId == null || kakaoId.isBlank() || visitDate == null || theme == null
            || !Set.of("cafe", "food", "walk", "shopping", "history", "culture").contains(theme))
            throw new IllegalArgumentException("Kakao ID, valid theme and visitDate required");
        JsonNode facility = official.get(kakaoId);
        if (facility != null && facility.path("recommendation").path("exclude").asBoolean(false))
            return result(kakaoId, "EXCLUDE", "team_policy", facility.path("recommendation").path("reason").asText(), null, facility);
        JsonNode mapping = mappings.get(kakaoId);
        if (mapping == null) return result(kakaoId, "KEEP_UNKNOWN", "unknown", "MAPPING_NOT_BUILT", null, null);
        String status = mapping.path("status").asText();
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
        if (facility != null && facility.path("verificationStatus").asText().equals("verified")
            && facility.hasNonNull("hours"))
            return result(kakaoId, "CHECK_HOURS", "official", "OFFICIAL_RULES_REQUIRE_EVALUATION", null, facility);
        if (facility != null && facility.path("verificationStatus").asText().equals("user_reported"))
            return result(kakaoId, "KEEP_UNKNOWN", "user_reported", "USER_HOURS_REQUIRE_CONFIRMATION", null, facility);
        return result(kakaoId, "KEEP_UNKNOWN", "unknown", "OFFICIAL_HOURS_UNCONFIRMED", null, facility);
    }
    private static Result result(String id, String decision, String source, String reason,
                                 GooglePlacesClient.Details google, JsonNode official) {
        return new Result(id, decision, source, reason, google, official);
    }
}
