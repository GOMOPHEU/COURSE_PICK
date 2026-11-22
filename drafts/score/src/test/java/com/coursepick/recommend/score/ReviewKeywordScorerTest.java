package com.coursepick.recommend.score;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewKeywordScorerTest {

    private static final List<String> FAMILY = List.of("아이", "아기", "가족", "유모차", "키즈");
    private static final List<String> GROUP_PENALTY = List.of("좁다", "자리 없다");

    @Test
    void 키워드가_들어간_리뷰_비율이_점수가_된다() {
        List<String> reviews = List.of(
                "아이랑 오기 좋아요",
                "커피 맛있음",
                "유모차 끌고 들어가기 편해요",
                "조용해요");
        assertEquals(0.5, ReviewKeywordScorer.score(reviews, FAMILY, List.of()), 1e-9);
    }

    @Test
    void 한_리뷰에_키워드가_여러_번_나와도_한_번만_센다() {
        List<String> reviews = List.of("아이 아기 가족 모두 좋아했어요", "별로");
        assertEquals(0.5, ReviewKeywordScorer.score(reviews, FAMILY, List.of()), 1e-9);
    }

    @Test
    void 감점_키워드는_빼고_0_아래로는_내려가지_않는다() {
        List<String> reviews = List.of("자리가 좁다", "자리 없다 진짜", "맛있어요");
        assertEquals(0.0, ReviewKeywordScorer.score(reviews, List.of("모임"), GROUP_PENALTY), 1e-9);
    }

    @Test
    void 리뷰가_없으면_0이다() {
        assertEquals(0.0, ReviewKeywordScorer.score(List.of(), FAMILY, List.of()), 1e-9);
        assertEquals(0.0, ReviewKeywordScorer.score(null, FAMILY, List.of()), 1e-9);
    }
}
