package com.coursepick.places;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Server-side, request-scoped Google details. Never persist or log response payloads. */
public final class GooglePlacesClient {
    public enum State { OK, NOT_FOUND, API_ERROR }
    public enum HoursBasis { CURRENT, REGULAR, UNKNOWN }
    public record Details(
        State state, String googlePlaceId, String businessStatus,
        Double rating, Integer userRatingCount, JsonNode currentOpeningHours,
        JsonNode regularOpeningHours, JsonNode attributions, String googleMapsUri,
        LocalDate fetchedDate, String errorCode
    ) {
        /** CURRENT covers today + 6 days, REGULAR is only a weekly estimate. */
        public HoursBasis hoursBasis(LocalDate visitDate) {
            Objects.requireNonNull(visitDate);
            if (state != State.OK || visitDate.isBefore(fetchedDate)) return HoursBasis.UNKNOWN;
            if (!visitDate.isAfter(fetchedDate.plusDays(6)) && hasPeriods(currentOpeningHours))
                return HoursBasis.CURRENT;
            return hasPeriods(regularOpeningHours) ? HoursBasis.REGULAR : HoursBasis.UNKNOWN;
        }
        public JsonNode hoursFor(LocalDate date) {
            return switch (hoursBasis(date)) {
                case CURRENT -> currentOpeningHours;
                case REGULAR -> regularOpeningHours;
                case UNKNOWN -> null;
            };
        }
        private static boolean hasPeriods(JsonNode hours) {
            return hours != null && hours.path("periods").isArray();
        }
    }

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String FIELDS = "id,businessStatus,rating,userRatingCount,currentOpeningHours,regularOpeningHours,attributions,googleMapsUri";
    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI base;
    private final Clock clock;

    public GooglePlacesClient(String apiKey) {
        this(apiKey, URI.create("https://places.googleapis.com/v1/places/"), Clock.system(SEOUL));
    }

    // Package-private base override for local mock tests only; production never follows redirects.
    GooglePlacesClient(String apiKey, URI base, Clock clock) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("\n") || apiKey.contains("\r"))
            throw new IllegalArgumentException("GOOGLE_PLACES_API_KEY is required");
        this.apiKey = apiKey;
        this.base = base;
        this.clock = clock;
        this.json = new ObjectMapper();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Details fetch(String placeId) {
        if (placeId == null || !placeId.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid Google Place ID");
        LocalDate date = LocalDate.now(clock.withZone(SEOUL));
        HttpRequest request = HttpRequest.newBuilder(base.resolve(placeId + "?languageCode=ko&regionCode=KR"))
            .header("X-Goog-Api-Key", apiKey).header("X-Goog-FieldMask", FIELDS)
            .timeout(Duration.ofSeconds(12)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return failure(State.NOT_FOUND, placeId, date, "NOT_FOUND");
            if (response.statusCode() != 200)
                return failure(State.API_ERROR, placeId, date, "HTTP_" + response.statusCode());
            JsonNode data = json.readTree(response.body());
            if (data == null || !data.isObject() || !placeId.equals(data.path("id").asText()))
                return failure(State.API_ERROR, placeId, date, "INVALID_RESPONSE");
            return new Details(State.OK, placeId, data.path("businessStatus").asText("UNKNOWN"),
                data.path("rating").isNumber() ? data.get("rating").doubleValue() : null,
                data.path("userRatingCount").isIntegralNumber() ? data.get("userRatingCount").intValue() : null,
                objectOrNull(data.get("currentOpeningHours")), objectOrNull(data.get("regularOpeningHours")),
                data.path("attributions").isArray() ? data.get("attributions") : json.createArrayNode(),
                data.path("googleMapsUri").asText(null), date, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure(State.API_ERROR, placeId, date, "INTERRUPTED");
        } catch (IOException e) {
            return failure(State.API_ERROR, placeId, date, "IO_OR_INVALID_JSON");
        }
    }

    public Optional<List<JsonNode>> searchText(String query, double lat, double lng) {
        try {
            var body = json.createObjectNode();
            body.put("textQuery", query);
            body.put("languageCode", "ko");
            body.put("regionCode", "KR");
            body.put("pageSize", 5);
            var center = body.putObject("locationBias").putObject("circle").putObject("center");
            center.put("latitude", lat);
            center.put("longitude", lng);
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("locationBias").path("circle")).put("radius", 500.0);
            URI uri = URI.create(base.toString().replaceAll("places/$", "places:searchText"));
            HttpRequest request = HttpRequest.newBuilder(uri)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(12))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            JsonNode data = json.readTree(response.body());
            List<JsonNode> places = new ArrayList<>();
            if (data != null && data.path("places").isArray()) data.get("places").forEach(places::add);
            return Optional.of(places);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static JsonNode objectOrNull(JsonNode value) {
        return value != null && value.isObject() ? value : null;
    }
    private static Details failure(State state, String id, LocalDate date, String code) {
        return new Details(state, id, "UNKNOWN", null, null, null, null, null, null, date, code);
    }
}
