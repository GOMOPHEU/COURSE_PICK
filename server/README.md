# coursepick-server

코스픽(CoursePick) 추천 서버입니다. 앱의 설문 값을 받아 카카오 후보 조회, 구글 검증, 코스 구성, 경로 API 시간 검증을 거친 코스를 돌려줍니다. 현재는 앱 개발용으로 `/recommend`가 고정된 가짜 응답을 돌려주는 상태입니다.

## 환경

| 항목 | 값 |
|---|---|
| Java | 17 (JDK 17 이상 필요) |
| 빌드 도구 | Gradle 8.14.3 (프로젝트에 포함된 Gradle Wrapper 사용, 별도 설치 불필요) |
| 프레임워크 | Spring Boot 3.5.0 |
| 서버 포트 | 8080 |
| DB | 사용하지 않음. 설정은 `config/coursepick.json` 파일로 관리 |

Java 버전은 `java -version`으로 확인하세요. 17 이상이면 그대로 빌드됩니다.

## 실행 방법

처음 실행할 때는 인터넷이 연결되어 있어야 합니다. Gradle과 라이브러리를 내려받습니다.

Windows PowerShell:

```
.\gradlew.bat bootRun
```

macOS / Linux:

```
./gradlew bootRun
```

실행 후 확인:

```
curl http://localhost:8080/health
```

`status`가 `ok`이고 `region`이 `안국역`으로 나오면 정상입니다.

## 테스트

```
.\gradlew.bat test
```

## API 키 설정

키는 환경변수로만 넣고 깃에 올리지 않습니다. 설정하지 않아도 서버는 실행되고, `/health`의 `keysConfigured`에서 어떤 키가 들어 있는지만 true/false로 보여 줍니다.

| 환경변수 | 용도 |
|---|---|
| `KAKAO_REST_API_KEY` | 카카오 로컬 API와 길찾기 API (같은 앱 키를 사용) |
| `GOOGLE_PLACES_API_KEY` | 구글 Places API (New) |
| `GEMINI_API_KEY` | Gemini (11/25 이후 연결 예정) |

PowerShell에서 키를 넣고 실행하는 예:

```
$env:KAKAO_REST_API_KEY="여기에_키"
.\gradlew.bat bootRun
```

## 앱에서 접속하는 주소

| 환경 | 주소 |
|---|---|
| 안드로이드 에뮬레이터 | `http://10.0.2.2:8080` |
| 같은 와이파이의 실제 폰 | `http://서버를_돌리는_PC의_IP:8080` |

## API

| 주소 | 설명 | 상태 |
|---|---|---|
| `GET /health` | 서버 상태, 설정된 지역, 키 설정 여부 | 완료 |
| `POST /recommend` | 설문 값을 받아 코스 1~3개를 돌려줌 | 가짜 응답 |
| `GET /places/{kakaoId}?theme=food&date=2026-11-28` | 카카오 ID로 구글 조회(별점, 영업시간, 폐업 여부). `theme`은 cafe, food, walk, shopping, history, culture 중 하나, `date`는 생략하면 오늘. `GOOGLE_PLACES_API_KEY`가 없으면 503 | 완료 |

요청과 응답 형식은 팀 할일 목록 v2의 12-1절을 따릅니다.

## 설정 파일

`config/coursepick.json`에 지역, 체류시간, 식사 시간대, 휴관일, 점수 가중치, 동행별 규칙을 둡니다. 다른 위치의 파일을 쓰려면 `COURSEPICK_CONFIG` 환경변수에 경로를 지정합니다. 지역 좌표는 예시 값이라 check19에서 쓴 좌표로 맞춰야 합니다.

## 폴더 구조

```
src/main/java/com/coursepick
├── CoursepickApplication.java
├── config        설정 파일과 API 클라이언트 설정
├── controller    요청을 받는 곳
├── places        구글 장소 조회 (정환 작성, mobile/tools/place-data에서 이동)
├── dto           앱과 주고받는 요청·응답 형식
└── service       추천 로직 (현재는 가짜 응답)
config/coursepick.json
config/place-mappings.json    카카오 ID와 구글 장소 ID 연결표 (정환 작성, 데모 표본 90곳)
config/official-hours.json    구글에 없는 시설의 공식 운영시간 기록
```

## 개발 규칙

- API 키는 환경변수로만 관리하고 깃에 올리지 않습니다. 앱에는 키를 넣지 않습니다.
- 각자 브랜치에서 작업하고 main에는 PR로 합칩니다.
- 카카오와 구글에서 받은 장소 정보는 저장하지 않고 요청할 때 호출합니다.
