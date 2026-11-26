# 코스픽 서버 API와 추천 흐름

앱 개발자용 요청·응답 형식과, 서버가 코스를 만드는 흐름(check19 로직의 Java 구현)을 정리한 문서입니다. 실행 방법과 키 설정은 [README](README.md)를 봅니다.

## 1. 한눈에 보는 흐름

`POST /recommend` 하나가 아래 순서로 처리됩니다.

| 단계 | 하는 일 |
|---|---|
| 1. 입력 확인 | 출발 지점, 날짜, 시각(HH:mm), 테마를 확인합니다. 잘못되면 400 `INVALID_INPUT` |
| 2. 시간표 계산 | 출발~종료 시각을 칸으로 나눕니다. 식사 시간대(점심 시작 11:00~13:30, 저녁 시작 17:00~19:30)에 쓸 수 있는 시간이 65분 이상이면 식당을 넣습니다. 장소마다 체류시간 범위가 있고, 카페는 하루 3곳 이하에 4시간 간격, 같은 테마는 연속하지 않습니다. 마지막에 귀가 여유 15분을 남깁니다 |
| 3. 후보 조회 | 카카오 로컬 API로 테마별 후보를 가져옵니다. 출발 지점 주변 ±100m에서 시작해 100, 200, 300, 500, 700, 1000m 바깥 띠로 넓혀 갑니다 |
| 4. 후보 거르기 | 휴관일, 술집(가족·10대), 한 구간 이동 한도(도보 추정 = 직선거리 × 1.3 ÷ 분당 67m), 이미 고른 곳과 중복, 앞선 코스에서 쓴 곳을 뺍니다 |
| 5. 구글 검증 | 매칭표(`config/place-mappings.json`)에 없으면 구글 Text Search로 실시간 매칭합니다. 영업 중인지(도착~출발 전체)와 폐업 여부를 확인하고, 매칭 실패나 영업시간을 모르는 곳은 뺍니다. 필수 방문지만 예외입니다. 경복궁·창덕궁·창경궁은 공식 운영시간(`config/official-hours.json`)을 씁니다 |
| 6. 점수와 선택 | 거리, 보정 별점, 테마 일치, 동행, 영업 확인 여부 가중치로 점수를 매겨 칸마다 한 곳을 고릅니다 |
| 7. 코스 조립 | 코스를 서로 장소가 겹치지 않게 여러 개 만듭니다. Gemini가 켜져 있으면 5개, 꺼져 있으면 3개입니다 |
| 8. 경로 검증 | 카카오맵 도보·대중교통 길찾기로 가장 긴 구간 3개를 실제 시간으로 바꾸고, 종료 시각을 넘기면 앞 장소의 체류를 줄여 맞춥니다. 조회에 실패하면 `ROUTE_FAIL` |
| 9. Gemini 선택 | 만들어진 코스 중 사용자 조건에 가장 맞는 3개를 Gemini가 고르고 코스마다 추천 이유 한 문장을 씁니다. 코스 내용은 바꾸지 않습니다. 호출이 실패하면 코드가 만든 순서를 그대로 씁니다 |

규칙은 코드가 지키고 Gemini는 선택과 이유만 맡습니다. Gemini에게 코스 구성까지 맡겨 봤을 때 규칙 위반으로 대부분 탈락해서 이 방식으로 정했습니다.

## 2. 주소

| 주소 | 설명 |
|---|---|
| `GET /health` | 서버 상태, 지역, 키 설정 여부 |
| `POST /recommend` | 코스 1~3개 추천 |
| `GET /places/{kakaoId}?theme=food&date=2026-11-28` | 장소 하나의 별점, 영업시간, 폐업 여부. `arrive`·`depart`(HH:mm)를 주면 `openCheck`(OPEN, CLOSED, UNKNOWN) 판정 |
| `GET /candidates` | 후보 조회 확인용 (개발용) |

카카오 키(`KAKAO_REST_API_KEY`)가 없으면 `/recommend`는 앱 화면 개발용 고정 응답을 돌려줍니다.

## 3. 추천 요청

```json
{
  "startPointId": "anguk-stn",
  "date": "2026-11-28",
  "startTime": "14:00",
  "endTime": "19:00",
  "transport": "walk",
  "themes": ["cafe", "history"],
  "companion": "family",
  "partySize": 4,
  "mobility": "minimizeWalking",
  "ageGroup": "10s",
  "mustVisit": "경복궁"
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `startPointId` | 필수 | `anguk-stn`, `gyeongbokgung-stn`, `jonggak-stn`, `gwanghwamun-stn` 중 하나 |
| `startTime`, `endTime` | 필수 | `HH:mm`. 종료가 출발보다 늦어야 함 |
| `themes` | 필수 | 1개 이상. `history`, `cafe`, `walk`, `culture`, `shopping`. 식당(`food`)은 식사 시간대에 자동으로 들어감 |
| `date` | 선택 | `yyyy-MM-dd`. 생략하면 오늘(한국 시간). 휴관일과 영업시간 판단에 쓰임 |
| `transport` | 선택 | `walk` 또는 `transit`. `transit`이면 길찾기 검증에 대중교통을 씀 |
| `companion` | 선택 | `family`: 술집 제외, 한 구간 15분 이하, 이름·분류에 아이·가족·키즈 등이 있으면 가산점. `couple`: 17:00 이후 카페·문화·산책 우선, 데이트·분위기·야경 등이 있으면 가산점. `friends`, `solo`: 모임·친구, 혼밥·1인 등 키워드 가산점 |
| `ageGroup` | 선택 | `10s`면 술집 제외 |
| `mobility` | 선택 | `minimizeWalking`이면 한 구간 도보 10분 이하 |
| `mustVisit` | 선택 | 장소 이름. 모든 코스에 넣음. 이 지역에서 못 찾으면 400 |
| `partySize` | 선택 | 받기만 하고 아직 추천에 반영하지 않음 |

## 4. 추천 응답

```json
{
  "courses": [
    {
      "courseId": "c1",
      "source": "basic",
      "places": [
        {
          "order": 1, "kakaoId": "8130598", "name": "경복궁", "theme": "history",
          "lat": 37.5796, "lng": 126.9770,
          "arriveAt": "14:10", "stayMinutes": 90, "openStatus": "confirmed"
        }
      ],
      "legs": [
        { "from": "start", "to": 1, "mode": "walk", "minutes": 10 }
      ],
      "totalMinutes": 175,
      "endAt": "16:55",
      "fit": "relaxed",
      "reason": "경복궁의 역사 관람 후 카페호미와 세종로공원으로 이어지는 알찬 도보 코스입니다.",
      "notes": ["역사: 후보 4곳 중 앞선 코스에서 쓴 곳 3곳, 이동 시간 한도 초과 1곳"]
    }
  ],
  "state": "OK",
  "geminiNotes": ["제미나이가 코스 5개 중 3개를 골랐어요"]
}
```

| 필드 | 설명 |
|---|---|
| `state` | `OK`, `NO_COURSE`(조건에 맞는 코스 없음), `ROUTE_FAIL`(이동시간 조회 실패) |
| `courses[].source` | `ai`: 코드가 만든 코스 중 Gemini가 골라 이유를 쓴 코스. `basic`: Gemini 없이 코드가 만든 코스(Gemini 키가 없거나 호출이 실패한 경우) |
| `courses[].reason` | 추천 이유. Gemini가 켜져 있으면 Gemini 문장, 아니면 고정 문구 |
| `courses[].notes` | 선택한 테마가 이 코스에 없을 때의 이유. 모든 테마가 들어갔으면 빈 배열 |
| `legs[].mode` | `walk` 또는 `transit` |
| `openStatus` | `confirmed`(영업 확인) 또는 `unknown`(운영정보 미확인) |
| `fit` | `relaxed`(여유) 또는 `tight`(빠듯함) |
| `geminiNotes` | Gemini 호출 결과나 실패 사유. 화면에 보여 줄 필요는 없고 점검용 |

## 5. 오류 응답

| 상태 | `code` | 언제 |
|---|---|---|
| 400 | `INVALID_INPUT` | 필수값 누락, 시각 형식·순서 오류, 알 수 없는 출발 지점, 필수 방문지를 못 찾음, JSON 형식 오류 |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류 |
| 502 | `UPSTREAM_FAILURE` | 카카오·구글 호출 실패 |

본문은 `{"code": "...", "error": "사람이 읽을 수 있는 메시지"}`입니다.

## 6. 설정 파일과 키

- `config/coursepick.json`: 지역, 출발 지점, 체류시간, 식사 시간대, 휴관일, 가중치, 동행 규칙, Gemini 설정
- `config/place-mappings.json`: 카카오 ID와 구글 장소 ID 연결표 (정환님 작성, 데모 표본 90곳)
- `config/official-hours.json`: 구글에 없는 시설의 공식 운영시간 기록
- 키는 환경변수로만: `KAKAO_REST_API_KEY`, `GOOGLE_PLACES_API_KEY`, `GEMINI_API_KEY`

## 7. 시나리오로 확인하기

`docs/scenarios` 폴더의 요청 파일(`s1-family-saturday.json`, `s2-couple-weekday-evening.json`, `s3-teen-friends-tuesday.json`, `e1-invalid-time.json`)을 서버에 보내 확인합니다.

```powershell
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$r = curl.exe -s -X POST http://localhost:8080/recommend -H "Content-Type: application/json" --data-binary "@docs/scenarios/s1-family-saturday.json" | ConvertFrom-Json
$r.geminiNotes
$r.courses | ForEach-Object { "-- $($_.courseId) $($_.source) $($_.reason)"; $_.places | ForEach-Object { "$($_.arriveAt) $($_.name) [$($_.theme)] $($_.stayMinutes)분" }; $_.notes }
```

## 8. 알려진 한계

- 도보 한도(최소 도보 10분 등)는 후보를 고를 때 추정값 기준입니다. 경로 검증 뒤 실측 시간이 최대 15분 안팎까지 나올 수 있습니다.
- 영업시간을 모르는 곳은 뺍니다. 구글 매칭이 약한 문화시설과 관광명소가 후보에서 많이 빠질 수 있습니다.
- 공식 운영시간은 경복궁·창덕궁·창경궁만 확인되어 있습니다. 종묘는 휴관일(매주 화요일, 공휴일인 경우에만 개방)만 설정에 있고, 공휴일 달력이 없어 공휴일 화요일도 휴관으로 처리합니다.
- 요청 한 번에 구글 상세 조회 30건, 검색 60건 상한이 있고, 실시간 매칭은 하루 검색 300회 상한입니다. 후보가 많은 테마는 상한에 걸려 코스가 줄 수 있습니다.
- Gemini가 켜져 있으면 코스를 5개 만들어서 구글·길찾기 호출이 늘어납니다. Gemini가 과부하(503)면 재시도 후 기본 순서로 나갑니다.
- 동행 규칙의 가산점은 장소 이름과 분류에 키워드가 있을 때만 줍니다. 구글 속성(아이 동반, 단체 이용 등)과 리뷰 키워드는 데이터를 못 가져와 반영하지 못했습니다. `partySize`도 아직 반영하지 않습니다.
