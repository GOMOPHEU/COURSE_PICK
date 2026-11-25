package com.coursepick.places;

import static org.assertj.core.api.Assertions.assertThat;

import com.coursepick.kakao.KakaoPlace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceMatcherTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private KakaoPlace kakao(String name, String road, double lat, double lng) {
        return new KakaoPlace("k1", name, "음식점 > 카페", "CE7", "", road, lat, lng, 0, "");
    }

    private JsonNode google(String id, String name, String address, double lat, double lng) throws Exception {
        return JSON.readTree("""
                {"id": "%s", "displayName": {"text": "%s"}, "formattedAddress": "%s",
                 "location": {"latitude": %s, "longitude": %s}}
                """.formatted(id, name, address, lat, lng));
    }

    @Test
    void matchesExactNameWithinFortyMeters() throws Exception {
        PlaceMatcher.Match match = PlaceMatcher.classify(
                kakao("봉구커피", "서울 종로구 율곡로 10", 37.5759, 126.9853),
                List.of(google("g1", "봉구커피", "대한민국 서울특별시 종로구 율곡로 10", 37.57592, 126.98531)));

        assertThat(match.status()).isEqualTo("matched");
        assertThat(match.googlePlaceId()).isEqualTo("g1");
    }

    @Test
    void sameNameFarAwayIsNotMatched() throws Exception {
        PlaceMatcher.Match match = PlaceMatcher.classify(
                kakao("이디야커피 인사점", "서울 종로구 인사동길 10", 37.5759, 126.9853),
                List.of(google("g2", "이디야커피 인사점", "서울 강남구 테헤란로 1", 37.50, 127.03)));

        assertThat(match.status()).isNotEqualTo("matched");
        assertThat(match.googlePlaceId()).isNull();
    }

    @Test
    void closeScoresAreAmbiguousAndGoToReview() throws Exception {
        PlaceMatcher.Match match = PlaceMatcher.classify(
                kakao("브람스", "서울 종로구 율곡로 20", 37.5769, 126.9859),
                List.of(
                        google("g3", "브람스", "서울 종로구 율곡로 20", 37.57692, 126.98591),
                        google("g4", "브람스", "서울 종로구 율곡로 20", 37.57693, 126.98592)));

        assertThat(match.status()).isEqualTo("review");
        assertThat(match.candidateIds()).containsExactly("g3", "g4");
    }

    @Test
    void noCandidatesIsUnmatched() {
        PlaceMatcher.Match match = PlaceMatcher.classify(kakao("없는집", "", 37.57, 126.98), List.of());

        assertThat(match.status()).isEqualTo("unmatched");
    }

    @Test
    void similarityFollowsLongestCommonBlocks() {
        assertThat(PlaceMatcher.ratio("abcd", "abcd")).isEqualTo(1.0);
        assertThat(PlaceMatcher.ratio("봉구커피", "봉구커피안국")).isBetween(0.7, 0.9);
        assertThat(PlaceMatcher.ratio("가나다", "라마바")).isEqualTo(0.0);
    }
}
