package com.coursepick.kakao;

public record KakaoPlace(
        String id,
        String name,
        String categoryName,
        String categoryGroupCode,
        String address,
        String roadAddress,
        double lat,
        double lng,
        int distanceM,
        String placeUrl) {
}
