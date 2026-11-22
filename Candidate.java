package com.coursepick.recommend.score;

/**
 * 점수를 매길 후보 장소 하나.
 * 필터(테마·동행 규칙·영업시간·휴관일)를 통과한 뒤의 후보를 넣는다.
 *
 * @param kakaoId        카카오 장소 ID
 * @param distanceMeters 이전 장소(또는 출발 지점)에서의 거리(m)
 * @param rating         구글 별점. 구글 매칭이 안 됐거나 별점이 없으면 null
 * @param reviewCount    구글 리뷰 수. 없으면 null
 * @param themeMatch     사용자가 고른 테마와 맞는 정도 0~1 (고른 테마면 1)
 * @param companionMatch 동행 규칙 가산 정도 0~1 (동행 유형을 안 골랐으면 0)
 * @param openVerified   방문 시간대 영업이 구글 또는 공식 안내로 확인됐는지
 * @param reviewKeyword  리뷰 키워드 점수 0~1 (ReviewKeywordScorer 결과)
 */
public record Candidate(
        String kakaoId,
        double distanceMeters,
        Double rating,
        Integer reviewCount,
        double themeMatch,
        double companionMatch,
        boolean openVerified,
        double reviewKeyword) {

    public Candidate {
        if (kakaoId == null || kakaoId.isBlank()) {
            throw new IllegalArgumentException("kakaoId가 비어 있습니다");
        }
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("거리는 0 이상이어야 합니다: " + distanceMeters);
        }
        themeMatch = clamp01(themeMatch);
        companionMatch = clamp01(companionMatch);
        reviewKeyword = clamp01(reviewKeyword);
    }

    static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }
}
