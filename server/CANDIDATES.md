# 영역 4 후보 선정 구현 및 인수인계

2026-11-26 기준, 담당 분배의 **2번 = 영역 4 전체**를 현재 Spring Boot 서버에 구현했다. 원문 문서의 NestJS는 현재 저장소 구조와 다르므로 기존 Java 서버를 사용한다.

## 확인 결과

기존 서버에는 카카오 RestClient 설정과 가족 규칙 일부만 있었고, 실제 후보 검색·필터 서비스는 없었다. `/recommend`는 고정 샘플 응답이었다. `docs/recommend-config.draft.json`은 초안이며 실행 코드가 아니다. 기존 `mobile/tools/place-data`는 사전 매칭 표본 도구로, 요청 시점 후보 선정 및 쇼핑 허용 분류 필터를 대체하지 않는다.

추가한 `CandidateService.find`는 서버에서 바로 호출할 수 있고, `POST /candidates`로 독립 실행할 수 있다. `/recommend`의 슬롯 계획·점수·구글 검증 연결은 다른 담당 영역이라 기존 샘플 응답을 유지한다. 앱의 코스 추천에 자동 반영된 상태는 아니다.

## 실행 및 호출

`server` 디렉터리에서 `KAKAO_REST_KEY`를 환경변수에 설정하고 `bash gradlew bootRun`을 실행한다. Java 17~24를 사용한다(현재 Gradle 8.14.3은 이 Mac의 Java 25 대신 설치된 Java 24로 검증).

```sh
curl -X POST http://localhost:8080/candidates \
  -H 'Content-Type: application/json' \
  -d '{"lat":37.5765,"lng":126.9855,"theme":"shopping","companion":"family","withChildren":true,"excludedIds":[],"excludedGroupKeys":[]}'
```

- `lat`, `lng`: 이전 장소 또는 출발점의 좌표. 필수.
- `theme`: `food`, `cafe`, `walk`, `history`, `exhibition`, `culture`, `shopping` 중 하나. 슬롯별 호출한다.
- `companion`: `solo`, `friends`, `family`, `couple`. 생략 가능.
- `ageGroup`: `10s`는 술집 제외. `withChildren: true`도 동행 유형과 무관하게 술집 제외.
- `excludedIds`: 이미 선택한 장소 ID. `excludedGroupKeys`: 이미 방문한 궁 등 장소 묶음. 코스 조립 담당이 선택된 후보의 **ID와 groupKey 모두** 다음 호출에 전달해야 궁 하위 건물 재선택을 막는다.

응답의 `candidates`에는 카카오 ID·이름·분류·좌표·주소·장소 링크·묶음 키·이전 장소와의 직선거리·`openStatus: unknown`이 있다. `radiusMeters`, `expanded`, `insufficient`로 검색 범위와 부족 여부를 확인한다. `eligibilityStage: category_and_companion`은 분류/동행 필터까지만 통과했다는 뜻이며 영업 가능 확정이 아니다. 구글 검증 담당은 방문 예정 시각의 영업 여부를 별도로 확인해야 한다.

잘못된 요청은 400, 키 미설정은 503, 카카오 오류/응답 형식 오류는 502다. 외부 장애를 후보 0건으로 처리하지 않으며 외부 오류 본문을 노출하지 않는다. 카카오 연결 제한 3초, 응답 제한 5초를 적용한다.

## 구현 정책과 잠정 결정

| 항목 | 현재 동작 |
|---|---|
| 수집·저장 | 요청 시 카카오 API 호출. DB/파일/전역 캐시 없음. **요청 간 TTL 0초**, 요청 내부 중복 제거만 사용. 응답 장소 목록을 앱이나 서버에서 영구 저장하지 않는다. |
| 반경 | 이전 장소 중심 690m. 필터 및 중복 제거 후 3곳 미만이면 1.5배 확대하되 865m로 제한. 문서의 분당 75m·우회계수 1.3 기준 도보 15분 ≈865m. 실제 도보 시간이 아닌 추정치. |
| 서비스 범위 | 검색 중심과 별개로 안국역 중심 1,300m 밖 후보 제외. |
| 페이지 | 거리순 15개 × 최대 2페이지. `is_end`이면 중단. 설정으로 최대 3페이지까지 지원하며 45개 노출 한계를 우회하지 않는다. |
| 맛집 | FD6, 분류 경로의 간식·구내식당·도시락 제외. |
| 카페 | CE7이면서 `음식점 > 카페` 경로만 허용. |
| 산책 | AT4, 도보여행 제외. 원문 규칙을 사용하며 초안의 공원/거리 한정 규칙과는 다름. |
| 역사 | AT4 중 문화유적 또는 고궁 분류. |
| 전시·문화 | 각각 CT1. |
| 쇼핑 | AT4 테마거리 + 시장/소품샵/인테리어장식/디자인문구/패션잡화/복합쇼핑몰 키워드. 결과 이름으로 허용하지 않고 분류 경로를 재검사. |
| 쇼핑 허용 | `가정,생활` 아래 시장·인테리어장식판매·디자인문구·패션잡화점·복합쇼핑몰. 문구 일반·액세서리는 잠정 제외. |
| 쇼핑 제외 | 통신판매·인터넷쇼핑몰·백화점·편의점·보석/귀금속·화장품. 편의점은 코스 후보로 사용하지 않음. |
| 시장 | 행사형을 포함한 시장 분류를 잠정 허용하되 운영정보는 미확인. 실제 개최/운영 여부는 영역 5 검증 또는 수동 조사 필요. |
| 동행 | 가족·10대·아이 동반은 술집 분류와 이름의 이자카야/호프/포차/앤바/사카바/주점 제외. 성인 혼자/친구/연인은 추가 강제 제외 없음. 선호 점수는 영역 6 담당. |
| 궁 중복 | 경복궁·창덕궁·창경궁·운현궁의 설정된 대표 좌표 주변에서 역사 분류 + 궁 이름 접두어 또는 명시적 하위 건물 이름으로 묶음. 실제 검색된 대표 궁이 적격이면 우선. 없으면 검색된 하위 건물 1곳만 유지. 인근 박물관·음식점은 합치지 않음. |

검색 반경·최소 후보 수·페이지·쇼핑 허용 분류/키워드·술집 이름·궁 묶음은 `config/coursepick.json`의 `candidateSearch`에서 변경한다. 기존 `companionRules`의 보너스/이동시간 규칙은 점수 담당용이며 이 필터가 사용하지 않는다. 궁 묶음의 좌표·반경·하위 이름 목록은 잠정 수동 규칙이며 실제 분류/누락 건물에 따라 보완해야 한다.

## 쇼핑 매칭률 측정 check21

문서의 `check21.mjs` 역할을 Python 표준 라이브러리 기반 `tools/check21.py`로 추가했다. 실제 `/candidates`의 쇼핑 결과를 사용해 거리·시장·소품샵·몰별 매칭률과 영업시간 응답률을 출력한다. 기존 영역 5 `match_places.py`의 현재 매칭 판정을 그대로 재사용한다. 원문에 적힌 별도 정규화 알고리즘으로 교체하지 않았다.

```sh
# 저장소 루트에서, 서버를 실행한 상태로
python3 server/tools/check21.py --per-kind 5 --seed 21
# Google 키를 별도 파일에서 읽을 때
python3 server/tools/check21.py --env-file /path/to/private.env --per-kind 2
```

서버에는 `KAKAO_REST_KEY`, 도구에는 `GOOGLE_PLACES_KEY` 또는 기존 `GOOGLE_PLACES_API_KEY`가 필요하다. 기본 env 파일은 기존 `mobile/.env`다. 기본 표본 상한 20곳(4유형×5), 옵션 상한 40곳. Google 검색은 표본당 1회이며 장애 재시도는 기존 도구 기준 최대 2회 추가된다. [Google Text Search 문서](https://developers.google.com/maps/documentation/places/web-service/text-search)에 따라 영업시간 필드는 Enterprise SKU 대상이다. 별점·리뷰는 요청하지 않는다.

출력은 유형별 집계만 포함한다. 이름·주소·좌표·영업시간 원문은 저장하지 않는다. 매칭률 분모는 해당 유형 표본 수, 영업시간 응답률은 매칭 표본/전체 표본을 각각 분모로 보고한다. 표본이 없으면 0%로 단정하지 않고 null이다. API 오류 시 실행이 실패하며 실패를 미매칭으로 집계하지 않는다. 영업시간 필드의 응답 여부를 측정하며 지금 영업 중임을 뜻하지 않는다. 현재 후보 반경과 페이지에 한정된 표본이며 지역 전수 조사나 44곳 현장 조사가 아니다.

## 팀이 실제 조사해야 할 항목

44곳의 동행 정보와 행사형 시장 운영 여부는 코드로 사실을 만들어 넣을 수 없다. 장소별로 다음 필드를 조사하고 미확인은 null로 둔다: `kakaoId`, `soloSeating`, `groupSeating`, `childEntryAllowed`, `strollerAccessible`, `reservationRequired`, `sourceUrl`, `checkedAt`, `researcher`, `notes`. 이름/리뷰 원문 복사 대신 확인한 사실만 기록한다. 이번 변경은 조사 양식과 연결 지침을 제공하며 44곳 조사를 완료했다는 뜻은 아니다.

## 검증

```sh
cd server
bash gradlew test
cd ..
python3 -m unittest discover -s server/tools -p 'test_*.py' -v
```

서버 테스트는 테마 분류, 쇼핑 노이즈, 동행, 궁 중복, 선택 장소 제외, 서비스 범위, 반경 확장, 페이지 제한, 카카오 요청/오류, API 요청 검증을 다룬다. 실제 키와 네트워크를 쓰지 않는 모의 API 테스트다. `check21` 실측 결과는 아직 생성하지 않았다.

카카오 요청 계약은 [공식 로컬 API 문서](https://developers.kakao.com/docs/ko/local/dev-guide)를 기준으로 구현했다.
