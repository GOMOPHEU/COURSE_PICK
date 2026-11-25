package com.coursepick.dto;

import com.coursepick.places.GooglePlacesClient;
import com.coursepick.places.OpeningHoursChecker;
import com.coursepick.places.PlaceLookupService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        JsonNode official,
        String openCheck) {

    public static PlaceLookupResponse from(
            PlaceLookupService.Result result, LocalDate visitDate, LocalDateTime arrive, LocalDateTime depart) {
        GooglePlacesClient.Details google = result.google();
        String openCheck = openCheck(result, visitDate, arrive, depart);
        if (google == null) {
            return new PlaceLookupResponse(
                    result.kakaoId(), result.decision(), result.source(), result.reason(),
                    null, null, null, null, null, null, null, result.official(), openCheck);
        }
        return new PlaceLookupResponse(
                result.kakaoId(), result.decision(), result.source(), result.reason(),
                google.businessStatus(), google.rating(), google.userRatingCount(),
                google.hoursBasis(visitDate).name(), google.hoursFor(visitDate),
                google.attributions(), google.googleMapsUri(), result.official(), openCheck);
    }

    private static String openCheck(
            PlaceLookupService.Result result, LocalDate visitDate, LocalDateTime arrive, LocalDateTime depart) {
        if (arrive == null || depart == null || !"CHECK_HOURS".equals(result.decision())) {
            return null;
        }
        if ("official".equals(result.source())) {
            return OpeningHoursChecker.checkOfficial(result.official(), arrive, depart).name();
        }
        if (result.google() == null) {
            return OpeningHoursChecker.Status.UNKNOWN.name();
        }
        return OpeningHoursChecker.checkGoogle(result.google().hoursFor(visitDate), arrive, depart).name();
    }
}
