package com.coursepick.score;

import java.util.Collection;
import java.util.Objects;

/**
 * 리뷰 수를 반영한 별점 보정.
 *
 * 보정 별점 = (리뷰 수 × 별점 + m × C) ÷ (리뷰 수 + m)
 * m: 기준 리뷰 수(설정 rating.m, 기본 50)
 * C: 이번 요청에서 모은 후보들의 평균 별점
 *
 * 리뷰가 적은 장소의 별점이 평균 쪽으로 당겨져,
 * 리뷰 몇 개로 받은 5.0점이 많은 리뷰의 4.5점을 이기지 못하게 한다.
 */
public final class RatingCorrector {

    private final double m;

    public RatingCorrector(double m) {
        if (m < 0) {
            throw new IllegalArgumentException("m은 0 이상이어야 합니다: " + m);
        }
        this.m = m;
    }

    /**
     * 후보들의 평균 별점(C). 별점이 없는 후보는 빼고 계산한다.
     * 별점 있는 후보가 하나도 없으면 fallback을 돌려준다.
     */
    public static double priorMean(Collection<Double> ratings, double fallback) {
        double sum = 0;
        int count = 0;
        for (Double r : ratings) {
            if (r != null) {
                sum += r;
                count++;
            }
        }
        return count == 0 ? fallback : sum / count;
    }

    /**
     * 보정 별점. 별점이나 리뷰 수가 없으면 C를 그대로 쓴다.
     */
    public double correct(Double rating, Integer reviewCount, double priorMean) {
        if (rating == null || reviewCount == null || reviewCount <= 0) {
            return priorMean;
        }
        double n = reviewCount;
        return (n * rating + m * priorMean) / (n + m);
    }

    public double m() {
        return m;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RatingCorrector other && Double.compare(m, other.m) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m);
    }
}
