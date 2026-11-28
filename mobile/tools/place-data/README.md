# 정환 담당 장소 데이터 모듈

전체 결과·테마별 분석·90건 목록·미확인 시설·검증 결과·남은 작업은 [결과 및 인수인계 정리](RESULTS.md)를 먼저 확인하세요.

모바일에 번들하지 않는 서버/사전 작업용 폴더입니다. 종욱님 서버가 아직 없으므로 독립 Java 모듈로 작성했습니다. Spring Boot 프로젝트가 생기면 `java/src/main/java/kr/coursepick/places`와 `data/`를 서버로 옮깁니다. 현재 앱과 서버의 추천 API를 연결한 상태는 아닙니다.

## 범위와 저장 방식

- 안국역 좌표를 카카오에서 실행 시 조회하고 반경 1,300m로 검색합니다. 좌표 원문은 저장하지 않습니다.
- 카페·맛집·산책·쇼핑·역사·전시문화별 최대 15곳, 중복 제거 후 최대 90곳의 **데모 표본**입니다. 지역 전체 목록이 아닙니다. 카카오 검색별 노출 상한 45개도 있어 대규모 전수 수집으로 해석하면 안 됩니다.
- 주요 궁궐·박물관을 우선 선택합니다. 키워드에 걸린 음식점, 주차장, 화장실, 관리사무소 등은 분류/이름으로 제외합니다.
- `data/place-mappings.json`: 카카오 ID, Google Place ID, 매칭 상태, 후보 ID, 팀의 테마 태그와 확인 날짜만 저장합니다. 장소 이름·주소·좌표·별점·영업시간·리뷰 원문은 저장하지 않습니다.
- `data/official-hours.json`: 미매칭 관광·문화·산책 시설의 공식 정보 확인 기록입니다. `hours: null`, `closedDays: null`은 미확인입니다. 공원이나 집터라는 이유로 24시간 개방 또는 휴무 없음으로 추정하지 않습니다.
- 영업시간·별점은 Java가 Place Details (New)에서 요청마다 조회합니다. 리뷰는 요청하지 않습니다. 결과는 추천 요청 안에서만 사용합니다.

## 실행

프로젝트 루트(`mobile`)의 `.env`:

```dotenv
KAKAO_REST_API_KEY=...
GOOGLE_PLACES_API_KEY=...
```

따옴표는 선택입니다. 서버 키에 `EXPO_PUBLIC_`을 붙이지 마세요. `.env`는 Git에서 제외되어 있습니다. 운영 서버에서는 환경 변수로 주입하세요.

```sh
python3 tools/place-data/match_places.py
python3 -m unittest discover -s tools/place-data -p 'test_*.py' -v
python3 tools/place-data/test_java.py
# 실제 구글 조회 1건까지 확인 (호출 비용/쿼터 적용)
python3 tools/place-data/test_java.py --live
```

Python 3.10+, JDK 17+ 필요. Python 외부 패키지는 없습니다. Java 테스트 스크립트는 필요한 Jackson jar만 Maven Central에서 받습니다. Maven을 사용한다면 `mvn -f tools/place-data/java/pom.xml package`로 빌드할 수 있습니다. `PlacesTest`는 독립 실행형 테스트이므로 검증은 위 Python 테스트 실행기로 실행하세요.

이 Mac의 Python 인증서 설치가 안 되어 있으면 `SSL_CERT_FILE=/etc/ssl/cert.pem`을 명령 앞에 붙입니다. TLS 검증을 끄지 않습니다.

기존 매칭/검토/미매칭 결과는 재사용합니다. `--refresh`로 검토·미매칭만 다시 조회할 수 있습니다. 확정 매칭까지 다시 조사하려면 별도 `--output /private/tmp/place-mappings-refresh.json`을 지정해 비교하세요. 결과 파일은 원자적으로 교체합니다. `--max-per-theme 3`으로 작은 표본 실행도 가능합니다.

## 매칭 상태와 검토

| 상태 | 의미 | 처리 |
|---|---|---|
| matched | 이름과 근접 좌표, 또는 이름·도로명·건물번호·거리 조건 통과 | Java 조회에 사용 |
| review | 후보가 있지만 동일 장소라고 확정할 근거 부족 | 수동 검토 전 추천 후보 보류 |
| unmatched | 이번 제한된 검색에서 타당한 후보를 찾지 못함 | 음식점·카페 제외, 나머지 공식 정보/미확인 |
| api_error | 인증·네트워크·쿼터 등 조회 실패 | 폐업/미매칭으로 취급하지 않고 unknown |

`matched`는 자동 판정이며 사람의 현장 확인을 의미하지 않습니다. 정확한 이름 + 40m 이내, 또는 이름/주소 일치 + 100~150m 이내를 요구하며, 상위 후보 점수가 비슷하면 검토로 남깁니다. 도로명·지번 차이, 넓은 궁궐의 대표 좌표 차이, 지점명 차이는 검토가 필요할 수 있습니다. `unmatched`도 폐업 확정이 아닙니다.

`runComplete`는 API 실행 완료 여부이며 모든 매칭이 확정됐다는 뜻이 아닙니다. 검색은 최대 5개의 구글 후보만 비교하므로 미매칭을 영구적으로 단정하지 마세요. API 인증/쿼터 오류가 나면 실행을 중단하고 이미 얻은 결과만 보존합니다. 정상 검색 요청 수는 `requestCounts`에 기록하며 재시도는 각 요청당 최대 2회 추가될 수 있습니다.

수동 확정할 때는 카카오 ID의 현재 장소와 `candidatePlaceIds`의 구글 장소를 비교하고, 확정한 `googlePlaceId`, `status: matched`, `reason: manual_review`, `checkedAt`을 기록하세요. 주소가 같아도 다른 층/지점이면 확정하지 않습니다. 구글 원문은 JSON에 추가하지 마세요.

## Java 통합

`pom.xml`의 Jackson 2 의존성 또는 해당 서버의 호환 Jackson 2 의존성이 필요합니다. Spring Boot 버전은 아직 정해지지 않아 프레임워크를 강제로 추가하지 않았습니다. Jackson 3 기반 서버에서는 이 모듈의 Jackson 2 패키지와 혼동하지 마세요.

```java
var client = new GooglePlacesClient(System.getenv("GOOGLE_PLACES_API_KEY"));
var service = new PlaceLookupService(
    client,
    Path.of("config/place-mappings.json"),
    Path.of("config/official-hours.json")
);
var result = service.lookup(kakaoId, "culture", visitDate);
// result.google().rating(), userRatingCount(), hoursFor(visitDate)
// result.decision(): EXCLUDE / NEEDS_REVIEW / KEEP_UNKNOWN / CHECK_HOURS
```

서비스는 초기화 시 ID 매칭/공식 설정만 읽으므로 서버에서 한 번 생성해 재사용하면 됩니다. Google 응답은 캐시하지 않습니다. JSON 파일 변경 후에는 서비스를 다시 생성하세요. `GET /places/{kakaoId}?date=...`나 추천 서비스에서 호출하고 기존 카카오 장소 데이터와 조합하면 됩니다.

중요한 반환 규칙:

- `rating`·`userRatingCount` 누락은 `null`이며 0점으로 바꾸지 않습니다.
- `currentOpeningHours`: 조회일 포함 7일. 그 밖의 미래 날짜는 `regularOpeningHours`를 주간 예상값으로 반환합니다. `hoursBasis`가 이를 구분합니다.
- `CHECK_HOURS`는 방문 가능 확정이 아닙니다. 종욱님 시간 검증 로직에서 도착~출발 전체가 영업 구간에 포함되는지, 휴게시간·자정 넘김·공휴일·공식 휴관일을 확인해야 합니다. 현재 `openNow`로 미래 방문을 확정하면 안 됩니다.
- `periods: []`는 확인된 빈 영업 구간, 필드 누락은 정보 없음으로 구분합니다. 24시간 영업은 구글 공식 표현대로 `close`가 없는 구간을 고려해야 합니다.
- Google 403/429/5xx/네트워크 오류는 `KEEP_UNKNOWN`; 404는 오래된 ID일 수 있어 `NEEDS_REVIEW`. 이전 ID 미존재는 `MAPPING_NOT_BUILT`로 반환합니다.
- 구글 폐업/임시휴업 응답은 `EXCLUDE`. 미매칭 카페·음식점도 `EXCLUDE`. 미확인 문화시설은 `KEEP_UNKNOWN`입니다.
- 앱으로 반환하는 DTO에는 키·외부 응답 오류 본문을 넣지 마세요. 구글 정보 표시 시 Google Maps 표기 및 제3자 `attributions`를 반영하세요.

## 공식 정보 기록

공식 운영시간을 확인한 경우에만 `verificationStatus: verified`로 바꾸고, 출처 URL과 `checkedAt`을 기록합니다. 정기 휴관일이 공휴일이면 다음 날 휴관하는 규칙, 계절별 시간, 입장 마감, 특별 휴관도 별도 명시해야 합니다. 오래된 기사나 프로그램 시간은 현재 시설 전체 운영시간으로 전용하지 않습니다.

```json
{
  "kakaoId": "실제 ID",
  "verificationStatus": "verified",
  "hours": {"weekly": [{"days": [2, 3, 4, 5, 6, 7], "open": "09:00", "close": "18:00"}]},
  "closedDays": {"isoWeekdays": [1], "exceptions": []},
  "sources": [{"url": "https://기관의-실제-안내주소", "checkedAt": "YYYY-MM-DD"}]
}
```

위 예시는 형식 설명일 뿐 실제 시설 데이터가 아닙니다. 요일은 ISO(월=1, 일=7), 시간대는 Asia/Seoul입니다. 공식 설정은 조회 서비스가 그대로 전달하며 시간 규칙 평가는 추천 서버 담당 범위입니다.

## 확인한 기술 문서

- [카카오 로컬 API](https://developers.kakao.com/docs/ko/local/dev-guide)
- [Google Text Search](https://developers.google.com/maps/documentation/places/web-service/text-search)
- [Google Place Details](https://developers.google.com/maps/documentation/places/web-service/place-details)
- [Google 영업시간 필드](https://developers.google.com/maps/documentation/places/web-service/reference/rest/v1/places#OpeningHours)
- [Google 저장·표시 정책](https://developers.google.com/maps/documentation/places/web-service/policies)

필드에 따라 Google 청구 SKU가 달라집니다. 예산 알림은 호출을 중단하지 않으므로 콘솔의 API 쿼터도 팀 예산에 맞게 설정하세요.

## 2026-11-26 사용자 운영정보 반영

`official-hours.json`에서 `user_reported`는 사용자 조사이며 공식 검증 완료가 아닙니다. `hours`에 시간을 구조화하고 `userReport`에 전달일과 출처 미제공을 기록합니다. 과거 `sources`는 새 시간을 증명하지 않습니다. Java는 `KEEP_UNKNOWN / user_reported / USER_HOURS_REQUIRE_CONFIRMATION`으로 반환합니다.

요일은 ISO 월=1~일=7입니다. `alwaysOpen`은 상시 개방 제보이며 `scope`로 외관·집터·실내·공원을 구분합니다. 공휴일 휴무와 전시 교체 휴관은 별도 방문일 확인이 필요합니다. 공평공원의 제보 시간은 공평도시유적전시관과 혼동 가능성이 있어 `userReport`에만 보존하고 `hours`는 null입니다.

예전사와 서울문화홍보원은 `recommendation.exclude: true`로 기록하며 Java가 매칭 여부보다 먼저 제외합니다. Spring 후보 서비스의 `candidateSearch.excludedPlaceIds`에도 동일 두 ID를 넣었습니다. 제외 정책 변경 시 두 목록을 함께 갱신하세요. 매칭표의 과거 90건은 조사 이력으로 보존하며 추천 후보 수와는 구분합니다.
