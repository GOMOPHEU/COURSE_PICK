package com.coursepick.places;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleCandidateTest {

    @TempDir
    Path dir;

    private PlaceLookupService service(boolean accept) throws Exception {
        Path mappings = dir.resolve("m.json");
        Files.writeString(mappings, """
                {"schemaVersion":1,"mappings":[
                 {"kakaoId":"1","status":"review","candidatePlaceIds":["g1"]},
                 {"kakaoId":"2","status":"review","candidatePlaceIds":["g1","g2"]}]}
                """);
        Path official = dir.resolve("o.json");
        Files.writeString(official, "{\"schemaVersion\":1,\"facilities\":[]}");
        PlaceLookupService service = new PlaceLookupService(new GooglePlacesClient("test"), mappings, official);
        service.acceptSingleCandidate(accept);
        return service;
    }

    @Test
    void keepsReviewWhenOptionIsOff() throws Exception {
        assertThat(service(false).lookup("1", "culture", LocalDate.of(2026, 12, 1)).decision())
                .isEqualTo("NEEDS_REVIEW");
    }

    @Test
    void keepsReviewWhenSeveralCandidates() throws Exception {
        assertThat(service(true).lookup("2", "culture", LocalDate.of(2026, 12, 1)).decision())
                .isEqualTo("NEEDS_REVIEW");
    }
}
