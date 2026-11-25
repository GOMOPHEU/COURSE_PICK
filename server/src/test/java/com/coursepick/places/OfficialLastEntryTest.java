package com.coursepick.places;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OfficialLastEntryTest {

    private static final String PALACE = """
            {
              "kakaoId": "18619553",
              "verificationStatus": "verified",
              "hours": {"weekly": [
                {"days": [1,2,3,4,5,6,7], "open": "09:00", "close": "17:00", "lastEntry": "16:00"}
              ]},
              "closedDays": {"isoWeekdays": [2], "exceptions": []},
              "sources": []
            }
            """;

    private JsonNode palace() throws Exception {
        return new ObjectMapper().readTree(PALACE);
    }

    private LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 11, day, hour, minute);
    }

    @Test
    void entryBeforeLastEntryIsOpenEvenIfVisitRunsUntilClosing() throws Exception {
        assertThat(OpeningHoursChecker.checkOfficial(palace(), at(28, 15, 30), at(28, 17, 0)))
                .isEqualTo(OpeningHoursChecker.Status.OPEN);
    }

    @Test
    void arrivalAfterLastEntryIsClosed() throws Exception {
        assertThat(OpeningHoursChecker.checkOfficial(palace(), at(28, 16, 30), at(28, 17, 0)))
                .isEqualTo(OpeningHoursChecker.Status.CLOSED);
    }

    @Test
    void stayingPastClosingTimeIsClosed() throws Exception {
        assertThat(OpeningHoursChecker.checkOfficial(palace(), at(28, 15, 45), at(28, 17, 30)))
                .isEqualTo(OpeningHoursChecker.Status.CLOSED);
    }

    private static final String SEASONAL = """
            {
              "kakaoId": "18619553",
              "verificationStatus": "verified",
              "hours": {"weekly": [
                {"days": [1,2,3,4,5,6,7], "months": [1,2,11,12], "open": "09:00", "close": "17:00", "lastEntry": "16:00"},
                {"days": [1,2,3,4,5,6,7], "months": [3,4,5,9,10], "open": "09:00", "close": "18:00", "lastEntry": "17:00"}
              ]},
              "closedDays": {"isoWeekdays": [2], "exceptions": []},
              "sources": []
            }
            """;

    @Test
    void usesTheTimetableOfTheVisitMonth() throws Exception {
        JsonNode seasonal = new ObjectMapper().readTree(SEASONAL);
        LocalDateTime november = LocalDateTime.of(2026, 11, 28, 16, 30);
        LocalDateTime march = LocalDateTime.of(2027, 3, 13, 16, 30);

        assertThat(OpeningHoursChecker.checkOfficial(seasonal, november, november.plusMinutes(30)))
                .isEqualTo(OpeningHoursChecker.Status.CLOSED);
        assertThat(OpeningHoursChecker.checkOfficial(seasonal, march, march.plusMinutes(60)))
                .isEqualTo(OpeningHoursChecker.Status.OPEN);
    }

    @Test
    void closedOnTuesday() throws Exception {
        assertThat(OpeningHoursChecker.checkOfficial(palace(), at(24, 10, 0), at(24, 11, 0)))
                .isEqualTo(OpeningHoursChecker.Status.CLOSED);
    }
}
