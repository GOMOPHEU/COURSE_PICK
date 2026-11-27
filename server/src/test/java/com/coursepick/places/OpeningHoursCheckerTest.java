package com.coursepick.places;

import static com.coursepick.places.OpeningHoursChecker.Status.CLOSED;
import static com.coursepick.places.OpeningHoursChecker.Status.OPEN;
import static com.coursepick.places.OpeningHoursChecker.Status.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OpeningHoursCheckerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text);
    }

    private static final String WEEKDAY_9_TO_18 = """
            {"periods":[
              {"open":{"day":1,"hour":9,"minute":0},"close":{"day":1,"hour":18,"minute":0}},
              {"open":{"day":2,"hour":9,"minute":0},"close":{"day":2,"hour":18,"minute":0}},
              {"open":{"day":3,"hour":9,"minute":0},"close":{"day":3,"hour":18,"minute":0}},
              {"open":{"day":4,"hour":9,"minute":0},"close":{"day":4,"hour":18,"minute":0}},
              {"open":{"day":5,"hour":9,"minute":0},"close":{"day":5,"hour":18,"minute":0}}]}
            """;

    @Test
    void visitInsideOpeningPeriodIsOpen() throws Exception {
        assertEquals(OPEN, OpeningHoursChecker.checkGoogle(
                json(WEEKDAY_9_TO_18), at("2026-11-30T10:00"), at("2026-11-30T11:30")));
    }

    @Test
    void visitThatEndsAfterClosingIsClosed() throws Exception {
        assertEquals(CLOSED, OpeningHoursChecker.checkGoogle(
                json(WEEKDAY_9_TO_18), at("2026-11-30T17:00"), at("2026-11-30T18:30")));
    }

    @Test
    void visitOnDayWithoutPeriodIsClosed() throws Exception {
        assertEquals(CLOSED, OpeningHoursChecker.checkGoogle(
                json(WEEKDAY_9_TO_18), at("2026-11-29T10:00"), at("2026-11-29T11:00")));
    }

    @Test
    void periodCrossingMidnightCoversBothSides() throws Exception {
        JsonNode hours = json("""
                {"periods":[{"open":{"day":6,"hour":22,"minute":0},"close":{"day":0,"hour":2,"minute":0}}]}
                """);
        assertEquals(OPEN, OpeningHoursChecker.checkGoogle(hours, at("2026-11-28T23:00"), at("2026-11-28T23:50")));
        assertEquals(OPEN, OpeningHoursChecker.checkGoogle(hours, at("2026-11-29T01:00"), at("2026-11-29T01:30")));
        assertEquals(CLOSED, OpeningHoursChecker.checkGoogle(hours, at("2026-11-29T01:30"), at("2026-11-29T02:30")));
    }

    @Test
    void periodWithoutCloseFromSundayMidnightMeansAlwaysOpen() throws Exception {
        JsonNode hours = json("{\"periods\":[{\"open\":{\"day\":0,\"hour\":0,\"minute\":0}}]}");
        assertEquals(OPEN, OpeningHoursChecker.checkGoogle(hours, at("2026-11-30T03:00"), at("2026-11-30T05:00")));
    }

    @Test
    void emptyPeriodsAreClosedAndMissingHoursAreUnknown() throws Exception {
        assertEquals(CLOSED, OpeningHoursChecker.checkGoogle(
                json("{\"periods\":[]}"), at("2026-11-30T10:00"), at("2026-11-30T11:00")));
        assertEquals(UNKNOWN, OpeningHoursChecker.checkGoogle(
                null, at("2026-11-30T10:00"), at("2026-11-30T11:00")));
        assertEquals(UNKNOWN, OpeningHoursChecker.checkGoogle(
                json("{}"), at("2026-11-30T10:00"), at("2026-11-30T11:00")));
    }

    @Test
    void departureNotAfterArrivalIsUnknown() throws Exception {
        assertEquals(UNKNOWN, OpeningHoursChecker.checkGoogle(
                json(WEEKDAY_9_TO_18), at("2026-11-30T11:00"), at("2026-11-30T10:00")));
    }

    private static final String OFFICIAL = """
            {"verificationStatus":"verified",
             "hours":{"weekly":[{"days":[2,3,4,5,6,7],"open":"09:00","close":"18:00"}]},
             "closedDays":{"isoWeekdays":[1],"exceptions":["2026-12-01"]}}
            """;

    @Test
    void officialHoursOpenOnNormalDay() throws Exception {
        assertEquals(OPEN, OpeningHoursChecker.checkOfficial(
                json(OFFICIAL), at("2026-12-02T10:00"), at("2026-12-02T11:00")));
    }

    @Test
    void officialClosedWeekdayAndExceptionDateAreClosed() throws Exception {
        assertEquals(CLOSED, OpeningHoursChecker.checkOfficial(
                json(OFFICIAL), at("2026-11-30T10:00"), at("2026-11-30T11:00")));
        assertEquals(CLOSED, OpeningHoursChecker.checkOfficial(
                json(OFFICIAL), at("2026-12-01T10:00"), at("2026-12-01T11:00")));
    }

    @Test
    void officialVisitPastClosingIsClosed() throws Exception {
        assertEquals(CLOSED, OpeningHoursChecker.checkOfficial(
                json(OFFICIAL), at("2026-12-02T17:30"), at("2026-12-02T18:30")));
    }

    @Test
    void unconfirmedOfficialRecordIsUnknown() throws Exception {
        JsonNode facility = json("{\"verificationStatus\":\"unconfirmed\",\"hours\":null}");
        assertEquals(UNKNOWN, OpeningHoursChecker.checkOfficial(
                facility, at("2026-12-02T10:00"), at("2026-12-02T11:00")));
    }
}
