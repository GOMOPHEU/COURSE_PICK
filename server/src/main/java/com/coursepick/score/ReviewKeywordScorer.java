package com.coursepick.score;

import java.util.List;

/**
 * 리뷰 키워드 점수 (0~1).
 *
 * 받아온 리뷰 중 가산 키워드가 들어간 리뷰 비율에서,
 * 감점 키워드가 들어간 리뷰 비율을 뺀다.
 * 리뷰 원문은 점수 계산에만 쓰고 저장하거나 AI에 넘기지 않는다.
 */
public final class ReviewKeywordScorer {

    private ReviewKeywordScorer() {
    }

    public static double score(List<String> reviews, List<String> bonusKeywords, List<String> penaltyKeywords) {
        if (reviews == null || reviews.isEmpty()) {
            return 0;
        }
        int bonus = 0;
        int penalty = 0;
        for (String text : reviews) {
            if (text == null) {
                continue;
            }
            if (containsAny(text, bonusKeywords)) {
                bonus++;
            }
            if (containsAny(text, penaltyKeywords)) {
                penalty++;
            }
        }
        double value = (double) (bonus - penalty) / reviews.size();
        return Math.max(0, Math.min(1, value));
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (keywords == null) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isBlank() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
