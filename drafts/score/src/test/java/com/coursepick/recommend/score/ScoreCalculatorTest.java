package com.coursepick.recommend.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = ScoreCalculator.withDefaults();

    private static Candidate place(String id, double meters, Double rating, Integer reviews) {
        return new Candidate(id, meters, rating, reviews, 1.0, 0.0, true, 0.0);
    }

    @Test
    void 기본_가중치_합은_1이다() {
        ScoreWeights w = ScoreWeights.defaults();
        double sum = w.distance() + w.rating() + w.theme() + w.companion() + w.openVerified() + w.review();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void 가중치_합이_1이_아니면_만들_수_없다() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreWeights(0.5, 0.5, 0.5, 0, 0, 0));
    }

    @Test
    void 리뷰_가산점을_끄면_나머지_가중치로_다시_나눈다() {
        ScoreWeights w = ScoreWeights.defaults().withoutReview();
        assertEquals(0.0, w.review(), 1e-9);
        assertEquals(1.0, w.distance() + w.rating() + w.theme() + w.companion() + w.openVerified(), 1e-9);
        assertEquals(0.25 / 0.95, w.distance(), 1e-9);
    }

    @Test
    void 거리_점수는_가까울수록_크고_반경_밖이면_0이다() {
        assertEquals(1.0, calculator.distanceScore(0), 1e-9);
        assertEquals(0.5, calculator.distanceScore(345), 1e-9);
        assertEquals(0.0, calculator.distanceScore(690), 1e-9);
        assertEquals(0.0, calculator.distanceScore(1000), 1e-9);
    }

    @Test
    void 조건이_같으면_리뷰_많은_45점이_리뷰_적은_50점보다_위에_온다() {
        // 거리·테마·영업 확인이 같을 때 별점 보정만으로 순서가 정해지는지 확인
        List<ScoredCandidate> ranked = calculator.scoreAll(List.of(
                place("dideo-coffee", 300, 5.0, 13),
                place("gukbingwan", 300, 4.5, 477),
                place("avg-a", 300, 4.2, 200),
                place("avg-b", 300, 3.1, 200)));
        assertEquals("gukbingwan", ranked.get(0).candidate().kakaoId());
    }

    @Test
    void 결과는_점수_높은_순이다() {
        List<ScoredCandidate> ranked = calculator.scoreAll(List.of(
                place("far", 650, 4.0, 100),
                place("near", 50, 4.0, 100),
                place("mid", 300, 4.0, 100)));
        assertEquals("near", ranked.get(0).candidate().kakaoId());
        assertEquals("mid", ranked.get(1).candidate().kakaoId());
        assertEquals("far", ranked.get(2).candidate().kakaoId());
        for (int i = 1; i < ranked.size(); i++) {
            assertTrue(ranked.get(i - 1).score() >= ranked.get(i).score());
        }
    }

    @Test
    void 영업_확인이_안_되면_그만큼_점수가_낮다() {
        Candidate verified = new Candidate("a", 300, 4.5, 100, 1.0, 0.0, true, 0.0);
        Candidate unknown = new Candidate("b", 300, 4.5, 100, 1.0, 0.0, false, 0.0);
        double diff = calculator.score(verified, 4.2).score() - calculator.score(unknown, 4.2).score();
        assertEquals(0.10, diff, 1e-9);
    }

    @Test
    void 별점이_없는_후보는_평균_별점으로_계산한다() {
        Candidate noRating = new Candidate("c", 0, null, null, 0, 0, false, 0);
        ScoredCandidate sc = calculator.score(noRating, 4.0);
        assertEquals(4.0, sc.correctedRating(), 1e-9);
        // 거리 1 × 0.25 + 별점 (4.0 ÷ 5) × 0.30
        assertEquals(0.25 + 0.30 * 0.8, sc.score(), 1e-9);
    }

    @Test
    void 점수는_0과_1_사이다() {
        Candidate best = new Candidate("best", 0, 5.0, 10_000, 1, 1, true, 1);
        Candidate worst = new Candidate("worst", 5_000, 0.0, 10_000, 0, 0, false, 0);
        double high = calculator.score(best, 4.2).score();
        double low = calculator.score(worst, 4.2).score();
        assertTrue(high <= 1.0 && high > 0.99);
        assertTrue(low >= 0.0 && low < 0.01);
    }

    @Test
    void 범위를_벗어난_입력값은_0과_1로_잘린다() {
        Candidate c = new Candidate("x", 0, 4.0, 10, 3.0, -1.0, true, Double.NaN);
        assertEquals(1.0, c.themeMatch(), 1e-9);
        assertEquals(0.0, c.companionMatch(), 1e-9);
        assertEquals(0.0, c.reviewKeyword(), 1e-9);
    }
}
