package com.coursepick.score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 후보 점수 계산.
 *
 * 점수 = 거리 × w1 + 보정 별점 × w2 + 테마 × w3 + 동행 × w4 + 영업 확인 × w5 + 리뷰 키워드 × w6
 * 각 항목은 0~1로 맞춘 뒤 가중치를 곱한다.
 * - 거리: 가까울수록 1, maxDistanceMeters 이상이면 0
 * - 보정 별점: 보정 별점 ÷ 5
 */
public final class ScoreCalculator {

    /** 후보 중 별점 있는 곳이 하나도 없을 때 쓰는 평균 별점. */
    public static final double DEFAULT_PRIOR_MEAN = 4.0;

    private final ScoreWeights weights;
    private final RatingCorrector ratingCorrector;
    private final double maxDistanceMeters;

    /**
     * @param weights           가중치 (reviewBonus가 꺼져 있으면 weights.withoutReview()를 넘긴다)
     * @param ratingCorrector   별점 보정기
     * @param maxDistanceMeters 거리 점수가 0이 되는 거리. 후보 검색 반경(candidateSearch.radiusMeters)을 쓴다
     */
    public ScoreCalculator(ScoreWeights weights, RatingCorrector ratingCorrector, double maxDistanceMeters) {
        if (maxDistanceMeters <= 0) {
            throw new IllegalArgumentException("maxDistanceMeters는 0보다 커야 합니다: " + maxDistanceMeters);
        }
        this.weights = weights;
        this.ratingCorrector = ratingCorrector;
        this.maxDistanceMeters = maxDistanceMeters;
    }

    /** 설정 파일 초안 기본값으로 만든다. */
    public static ScoreCalculator withDefaults() {
        return new ScoreCalculator(ScoreWeights.defaults(), new RatingCorrector(50), 690);
    }

    /**
     * 후보 목록 전체에 점수를 매기고 높은 순으로 돌려준다.
     * 평균 별점(C)은 이 목록에서 계산한다.
     */
    public List<ScoredCandidate> scoreAll(List<Candidate> candidates) {
        List<Double> ratings = new ArrayList<>();
        for (Candidate c : candidates) {
            ratings.add(c.rating());
        }
        double priorMean = RatingCorrector.priorMean(ratings, DEFAULT_PRIOR_MEAN);

        List<ScoredCandidate> result = new ArrayList<>();
        for (Candidate c : candidates) {
            result.add(score(c, priorMean));
        }
        result.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(sc -> sc.candidate().kakaoId()));
        return result;
    }

    /** 후보 하나의 점수. priorMean은 같은 요청 후보들의 평균 별점이다. */
    public ScoredCandidate score(Candidate c, double priorMean) {
        double corrected = ratingCorrector.correct(c.rating(), c.reviewCount(), priorMean);
        double score =
                weights.distance() * distanceScore(c.distanceMeters())
                + weights.rating() * (corrected / 5.0)
                + weights.theme() * c.themeMatch()
                + weights.companion() * c.companionMatch()
                + weights.openVerified() * (c.openVerified() ? 1.0 : 0.0)
                + weights.review() * c.reviewKeyword();
        return new ScoredCandidate(c, corrected, score);
    }

    double distanceScore(double meters) {
        return Math.max(0, 1 - meters / maxDistanceMeters);
    }
}
