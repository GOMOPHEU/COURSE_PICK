package com.coursepick.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TimeFitTest {

    @Test
    void 보고서_예시_165분_180분은_빠듯함이다() {
        // Ⅱ-2.3 예시: 14:00~17:00, 총 165분 → 91.7%
        assertEquals(TimeFit.TIGHT, TimeFit.of(165, 180));
    }

    @Test
    void 정확히_90퍼센트는_여유다() {
        assertEquals(TimeFit.RELAXED, TimeFit.of(162, 180));
    }

    @Test
    void 정확히_100퍼센트는_빠듯함이다() {
        assertEquals(TimeFit.TIGHT, TimeFit.of(180, 180));
    }

    @Test
    void 백퍼센트를_넘으면_시간_초과다() {
        assertEquals(TimeFit.OVER, TimeFit.of(181, 180));
    }

    @Test
    void 응답_fit_값() {
        assertEquals("relaxed", TimeFit.RELAXED.apiValue());
        assertEquals("tight", TimeFit.TIGHT.apiValue());
    }

    @Test
    void 이용_가능_시간이_0이하면_오류다() {
        assertThrows(IllegalArgumentException.class, () -> TimeFit.of(60, 0));
    }
}
