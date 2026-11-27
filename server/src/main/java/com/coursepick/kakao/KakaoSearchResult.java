package com.coursepick.kakao;

import java.util.List;

public record KakaoSearchResult(int totalCount, List<KakaoPlace> places) {
}
