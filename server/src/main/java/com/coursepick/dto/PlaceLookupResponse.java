package com.coursepick.dto;

import com.coursepick.places.GooglePlacesClient;
import com.coursepick.places.PlaceLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;

public record PlaceLookupResponse(
        String kakaoId,
        String decision,
        String source,
        String reason,
        String businessStatus,
        Double rating,
        Integer userRatingCount,
        String hoursBasis,
        JsonNode openingHours,
        JsonNode attributions,
        String googleMapsUri,
        JsonNode official) {

    public static PlaceLookupResponse from(PlaceLookupService.Result result, LocalDate visitDate) {
        GooglePlacesClient.Details google = result.google();
        if (google == null) {
            return new PlaceLookupResponse(
                    result.kakaoId(), result.decision(), result.source(), result.reason(),
                    null, null, null, null, null, null, null, result.official());
        }
        return new PlaceLookupResponse(
                result.kakaoId(), result.decision(), result.source(), result.reason(),
                google.businessStatus(), google.rating(), google.userRatingCount(),
                google.hoursBasis(visitDate).name(), google.hoursFor(visitDate),
                google.attributions(), google.googleMapsUri(), result.official());
    }
}
