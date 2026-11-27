package com.coursepick.score;

/**
 * 점수 가중치. 합은 1이어야 한다.
 * 값은 설정 파일(recommend-config.json)의 weights와 같다.
 */
public record ScoreWeights(
        double distance,
        double rating,
        double theme,
        double companion,
        double openVerified,
        double review) {

    private static final double SUM_TOLERANCE = 1e-6;

    public ScoreWeights {
        double[] all = {distance, rating, theme, companion, openVerified, review};
        double sum = 0;
        for (double w : all) {
            if (w < 0) {
                throw new IllegalArgumentException("가중치는 0 이상이어야 합니다: " + w);
            }
            sum += w;
        }
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("가중치 합이 1이 아닙니다: " + sum);
        }
    }

    /** 설정 파일 초안의 기본값. */
    public static ScoreWeights defaults() {
        return new ScoreWeights(0.25, 0.30, 0.15, 0.15, 0.10, 0.05);
    }

    /**
     * 리뷰 가산점을 끈 경우(reviewBonus = false).
     * 리뷰 가중치를 나머지 항목에 비율대로 나눠, 합이 계속 1이 되게 한다.
     */
    public ScoreWeights withoutReview() {
        double rest = 1.0 - review;
        if (rest <= 0) {
            throw new IllegalStateException("리뷰 외 가중치가 없습니다");
        }
        return new ScoreWeights(
                distance / rest,
                rating / rest,
                theme / rest,
                companion / rest,
                openVerified / rest,
                0.0);
    }
}
