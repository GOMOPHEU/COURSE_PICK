# 점수 계산 코드 (임시 위치)

서버(Spring Boot) 기본 틀이 올라오면 서버 프로젝트 안으로 옮길 코드입니다.

- `src/main/java/.../score/`: 별점 보정, 가중합 점수, 리뷰 키워드 점수, 시간 적합 판정
- `src/test/java/.../score/`: JUnit 5 테스트 27개
- 패키지 이름 `com.coursepick.recommend.score`는 서버 기본 패키지에 맞춰 바꿉니다
- 설정값 초안은 `docs/recommend-config.draft.json`에 있습니다 (서버로 옮길 때 `src/main/resources/`에 둠)

## 서버 쪽에서 넘겨줘야 하는 값 (Candidate)

| 필드 | 어디서 | 설명 |
|---|---|---|
| kakaoId | 카카오 로컬 API | 장소 ID |
| distanceMeters | 서버 계산 | 이전 장소(또는 출발 지점)에서의 거리 |
| rating, reviewCount | Google Places API | 별점, 리뷰 수. 매칭이 안 되면 null |
| openVerified | Google Places API 또는 공식 운영시간 JSON | 방문 시간대 영업이 확인됐는지 |
| themeMatch, companionMatch | 서버 규칙 | 0~1 |
| reviewKeyword | ReviewKeywordScorer | 동행 유형을 골랐을 때만, 0~1 |
