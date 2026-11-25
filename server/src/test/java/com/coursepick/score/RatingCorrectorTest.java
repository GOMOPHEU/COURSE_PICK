package com.coursepick.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RatingCorrectorTest {

    private final RatingCorrector corrector = new RatingCorrector(50);

    @Test
    void 리뷰가_적은_5점_카페는_평균쪽으로_당겨진다() {
        // 드디어커피: 5.0점, 리뷰 13개, C = 4.2
        assertEquals(4.37, corrector.correct(5.0, 13, 4.2), 0.005);
    }

    @Test
    void 리뷰가_많은_장소는_원래_별점에_가깝다() {
        // 국빈관: 4.5점, 리뷰 477개, C = 4.2
        assertEquals(4.47, corrector.correct(4.5, 477, 4.2), 0.005);
    }

    @Test
    void 보정하면_리뷰_많은_45점이_리뷰_적은_50점보다_앞선다() {
        double cafe = corrector.correct(5.0, 13, 4.2);
        double restaurant = corrector.correct(4.5, 477, 4.2);
        assertTrue(restaurant > cafe);
    }

    @Test
    void 별점이나_리뷰수가_없으면_평균을_쓴다() {
        assertEquals(4.2, corrector.correct(null, 100, 4.2), 1e-9);
        assertEquals(4.2, corrector.correct(4.8, null, 4.2), 1e-9);
        assertEquals(4.2, corrector.correct(4.8, 0, 4.2), 1e-9);
    }

    @Test
    void 평균_별점은_별점_없는_후보를_빼고_계산한다() {
        List<Double> ratings = Arrays.asList(4.0, null, 5.0);
        assertEquals(4.5, RatingCorrector.priorMean(ratings, 4.0), 1e-9);
    }

    @Test
    void 별점_있는_후보가_없으면_기본값을_쓴다() {
        List<Double> ratings = Arrays.asList(null, null);
        assertEquals(4.0, RatingCorrector.priorMean(ratings, 4.0), 1e-9);
    }

    @Test
    void m이_음수면_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> new RatingCorrector(-1));
    }
}
