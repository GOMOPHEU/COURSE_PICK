package com.coursepick.score;

/**
 * 코스 시간 적합성.
 *
 * 비율 = 예상 총 소요시간 ÷ 이용 가능 시간(종료 시각 − 출발 시각)
 * - 0.90 이하: 여유 (RELAXED, 응답 fit = "relaxed")
 * - 0.90 초과 ~ 1.00 이하: 빠듯함 (TIGHT, 응답 fit = "tight")
 * - 1.00 초과: 시간 초과 (OVER, 코스에서 제외)
 */
public enum TimeFit {
    RELAXED,
    TIGHT,
    OVER;

    public static TimeFit of(int totalMinutes, int availableMinutes) {
        return of(totalMinutes, availableMinutes, 0.90, 1.00);
    }

    public static TimeFit of(int totalMinutes, int availableMinutes, double relaxedMaxRatio, double tightMaxRatio) {
        if (availableMinutes <= 0) {
            throw new IllegalArgumentException("이용 가능 시간은 0보다 커야 합니다: " + availableMinutes);
        }
        if (totalMinutes < 0) {
            throw new IllegalArgumentException("총 소요시간은 0 이상이어야 합니다: " + totalMinutes);
        }
        // 정수 비교로 경계값(예: 162/180 = 정확히 90%)이 부동소수점 오차로 바뀌지 않게 한다
        if (totalMinutes * 100L <= Math.round(relaxedMaxRatio * 100) * (long) availableMinutes) {
            return RELAXED;
        }
        if (totalMinutes * 100L <= Math.round(tightMaxRatio * 100) * (long) availableMinutes) {
            return TIGHT;
        }
        return OVER;
    }

    /** API 응답의 fit 값. OVER는 응답에 나가지 않는다. */
    public String apiValue() {
        return switch (this) {
            case RELAXED -> "relaxed";
            case TIGHT -> "tight";
            case OVER -> "over";
        };
    }
}
