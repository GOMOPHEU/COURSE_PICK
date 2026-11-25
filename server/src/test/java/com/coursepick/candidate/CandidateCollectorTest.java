package com.coursepick.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coursepick.config.CoursepickSettings;
import com.coursepick.geo.Rect;
import com.coursepick.kakao.KakaoLocalClient;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.kakao.KakaoSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateCollectorTest {

    private static final double LAT = 37.5765;
    private static final double LNG = 126.9855;

    private static final String CONFIG = """
            {
              "region": {"name": "안국역", "lat": 37.5765, "lng": 126.9855, "radiusM": 1300},
              "candidateSearch": {
                "startHalfSizeM": 100, "stepM": 100, "maxHalfSizeM": 300,
                "pages": 2, "minEligible": 3, "maxSplitDepth": 1, "maxRequestsPerStep": 30,
                "themes": {
                  "cafe": {
                    "categoryCodes": ["CE7"],
                    "keywords": [],
                    "allowedCategoryPrefixes": ["음식점 > 카페"],
                    "excludeCategoryPrefixes": [],
                    "excludeNameKeywords": ["폐업"]
                  },
                  "history": {
                    "categoryCodes": ["AT4"],
                    "keywords": [],
                    "allowedCategoryPrefixes": ["여행 > 관광,명소 > 문화유적"],
                    "excludeCategoryPrefixes": [],
                    "excludeNameKeywords": [],
                    "collapseNamePrefix": true
                  }
                }
              }
            }
            """;

    private KakaoLocalClient kakao;
    private CandidateCollector collector;

    @BeforeEach
    void setUp() throws Exception {
        kakao = mock(KakaoLocalClient.class);
        CoursepickSettings settings = new CoursepickSettings(new ObjectMapper().readTree(CONFIG));
        collector = new CandidateCollector(kakao, settings);
    }

    private KakaoPlace place(String id, String name, String category, double lat, double lng) {
        return new KakaoPlace(id, name, category, "CE7", "", "", lat, lng, 0, "");
    }

    @Test
    void expansionStepsFollowConfig() {
        assertThat(collector.expansionSteps()).containsExactly(100, 200, 300);
    }

    @Test
    void firstStepSearchesOneSquareAndAppliesFilters() {
        KakaoPlace near = place("1", "가까운카페", "음식점 > 카페 > 커피전문점", 37.5767, 126.9858);
        KakaoPlace kids = place("2", "키즈카페", "가정,생활 > 유아 > 놀이시설 > 키즈카페", 37.5768, 126.9860);
        KakaoPlace outside = place("3", "서비스범위밖", "음식점 > 카페", 37.5900, 126.9855);
        KakaoPlace closed = place("4", "폐업카페", "음식점 > 카페", 37.5770, 126.9850);
        when(kakao.searchCategoryRect(eq("CE7"), any(Rect.class), eq(2)))
                .thenReturn(new KakaoSearchResult(4, List.of(near, kids, outside, closed)));

        List<KakaoPlace> result = collector.collectRing("cafe", LAT, LNG, 0, 100);

        assertThat(result).extracting(KakaoPlace::id).containsExactly("1");
        assertThat(result.get(0).distanceM()).isBetween(20, 60);
        verify(kakao, times(1)).searchCategoryRect(eq("CE7"), any(Rect.class), eq(2));
    }

    @Test
    void laterStepSearchesFourStripsAroundTheInnerSquare() {
        when(kakao.searchCategoryRect(eq("CE7"), any(Rect.class), eq(2)))
                .thenReturn(new KakaoSearchResult(0, List.of()));

        collector.collectRing("cafe", LAT, LNG, 100, 200);

        verify(kakao, times(4)).searchCategoryRect(eq("CE7"), any(Rect.class), eq(2));
    }

    @Test
    void ringStripsDoNotOverlapInnerSquare() {
        Rect inner = Rect.around(LAT, LNG, 100);
        for (Rect strip : Rect.ring(LAT, LNG, 100, 200)) {
            boolean overlaps = strip.west() < inner.east() && strip.east() > inner.west()
                    && strip.south() < inner.north() && strip.north() > inner.south();
            assertThat(overlaps).isFalse();
        }
    }

    @Test
    void saturatedSquareIsSplitIntoQuarters() {
        when(kakao.searchCategoryRect(eq("CE7"), any(Rect.class), eq(2)))
                .thenReturn(new KakaoSearchResult(100, List.of()));

        collector.collectRing("cafe", LAT, LNG, 0, 100);

        verify(kakao, times(5)).searchCategoryRect(eq("CE7"), any(Rect.class), eq(2));
    }

    @Test
    void collapsesPalaceBuildingsIntoRepresentative() {
        String cat = "여행 > 관광,명소 > 문화유적 > 고궁,궁";
        when(kakao.searchCategoryRect(eq("AT4"), any(Rect.class), eq(2)))
                .thenReturn(new KakaoSearchResult(4, List.of(
                        place("a", "창덕궁 영화당", cat, 37.5766, 126.9860),
                        place("b", "창덕궁", cat, 37.5790, 126.9910),
                        place("c", "운현궁", cat, 37.5762, 126.9870),
                        place("d", "운현궁 노안당", cat, 37.5763, 126.9871))));

        List<KakaoPlace> result = collector.collectRing("history", LAT, LNG, 0, 700);

        assertThat(result).extracting(KakaoPlace::name).containsExactlyInAnyOrder("창덕궁", "운현궁");
    }

    @Test
    void unknownThemeIsRejected() {
        assertThatThrownBy(() -> collector.collectRing("spa", LAT, LNG, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
