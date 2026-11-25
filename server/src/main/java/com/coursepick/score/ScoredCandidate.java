package com.coursepick.score;

/**
 * 점수가 매겨진 후보.
 *
 * @param candidate       원래 후보
 * @param correctedRating 보정 별점 (0~5)
 * @param score           최종 점수 (0~1, 클수록 우선)
 */
public record ScoredCandidate(Candidate candidate, double correctedRating, double score) {
}
