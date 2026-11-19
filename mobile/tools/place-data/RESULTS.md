# 장소 데이터 작업 결과 및 인수인계

기준일: 2026-11-19. 이 문서는 현재 저장된 JSON과 구현 코드를 기준으로 작성했다.

## 1. 현재 상태

카카오↔구글 매칭 도구, 실제 ID 매칭표, Java 영업시간·별점 조회 모듈과 테스트를 구현했다. 두 API 키의 실제 호출과 Java Place Details 조회 1건도 성공했다. **추천 서비스 전체가 완성된 상태는 아니다.** 종욱님 Spring Boot 프로젝트가 아직 없어 서버 엔드포인트 통합과 방문시간 검증은 남아 있다.

| 항목 | 결과 | 해석 |
|---|---:|---|
| 대상 | 90곳 | 안국역 반경 1.3km, 6개 테마별 15곳의 데모 표본 |
| 자동 매칭 | 40곳 (44.4%) | 설정한 비교 기준을 통과함. 수동 검증된 정확도 수치가 아님 |
| 검토 필요 | 31곳 (34.4%) | 후보 ID는 있으나 동일 장소로 자동 확정하지 못함 |
| 미매칭 | 19곳 (21.1%) | 제한된 검색에서 타당한 후보를 찾지 못함. 폐업 확정이 아님 |
| 저장된 API 오류 | 0곳 | 현재 JSON 기준. 개발 중 네트워크 실패가 없었다는 뜻은 아님 |
| 공식 운영정보 조사 | 11곳 | 현재 운영시간·휴관일 확정 0곳, 모두 미확인 |

매칭 40곳을 바로 “영업 확인된 장소 40곳”으로 표시하면 안 된다. 추천 요청 시 Java로 실제 영업 상태와 시간을 조회한 뒤 방문 시간 전체를 검증해야 한다.

## 2. 테마별 분석

| 테마 | 대상 | 자동 매칭 | 검토 필요 | 미매칭 | 자동 매칭 비율 |
|---|---:|---:|---:|---:|---:|
| 카페 | 15 | 6 | 6 | 3 | 40.0% |
| 맛집 | 15 | 8 | 6 | 1 | 53.3% |
| 산책 | 15 | 5 | 7 | 3 | 33.3% |
| 쇼핑 | 15 | 7 | 4 | 4 | 46.7% |
| 역사 | 15 | 8 | 4 | 3 | 53.3% |
| 전시·문화 | 15 | 6 | 4 | 5 | 40.0% |

- 역사·맛집은 각각 8곳으로 자동 매칭 수가 가장 많다. 산책은 5곳 자동 매칭, 7곳 검토로 검토 부담이 가장 크다. 전시·문화는 미매칭이 5곳으로 가장 많다.
- 역사·산책 시설은 넓은 부지, 부속 전각, 표석과 대표 지점 때문에 좌표나 이름이 다를 수 있다. 실제 조사에서도 궁궐 부속 건물과 집터가 검토/미매칭으로 남았다. 이는 가능한 설명이며 31건 전체의 원인을 확정한 분석은 아니다.
- 카페·음식점은 지점명이 중요하다. 가까운 동일 브랜드의 다른 지점을 연결하지 않도록 불확실한 결과를 보류했다.
- 표본이 테마마다 15개로 작고 가까운 장소를 우선하므로 서울 전체, 종로 전체, 테마별 Google 등록률로 일반화할 수 없다.

## 3. 검색 범위와 매칭 판정

안국역 좌표를 실행 때 카카오에서 확인한다. 반경 1,300m를 적용하고 검색마다 최대 3페이지(45개)를 조회한 뒤 분류 필터와 테마별 상한을 적용한다. 주요 궁궐·박물관을 우선 선택한다. 주차장·화장실·관리사무소와 역사 검색에 섞인 음식점은 제외한다.

Google Text Search에는 장소명과 주소, 주변 위치 힌트를 보내고 최대 5개 후보를 비교한다. 이름·거리·도로명/건물번호를 사용하며, 이름이 정확하고 40m 이내이거나 주소까지 일치하는 근접 후보 등을 자동 매칭한다. 상위 점수 차이가 작으면 검토로 남긴다. 지번/도로명 차이와 넓은 시설의 좌표 차이는 자동 매칭을 놓칠 수 있다. 같은 건물의 다른 시설을 잘못 연결할 가능성도 있어 자동 결과의 표본 검토가 필요하다.

현재 이유 필드의 `name_address_distance` 17건은 첫 실행의 더 엄격한 이름·주소·거리 기준을 통과해 재사용한 결과이고, `name_and_distance_or_address` 23건은 주소 형식 차이를 보완한 기준을 통과한 결과다. 상태 필드가 통합 계약이며 이유 필드는 판정 이력이다.

카카오 검색별 `searchTotal`은 API 검색 메타데이터다. 테마 간 중복 및 후처리 제외 전 수치이므로 합산해 실제 지역 장소 수로 해석하지 않는다. 상세 검색 범위는 [설정](config.json)과 [원본 매칭표](data/place-mappings.json)에 있다.

마지막 실행의 `requestCounts`는 카카오 33회, Google Text Search 2회다. 이전 결과를 재사용한 **마지막 실행만의 수치**이며 개발 전체 누적 호출·청구량이 아니다. 실제 비용은 콘솔에서 확인해야 한다. 이 보고서 작성을 위해 장소 API를 추가 호출하지 않았다.

## 4. 현재 추천 후보 처리

| 그룹 | 수 | Java 처리 |
|---|---:|---|
| 자동 매칭 | 40 | Google 상세 조회 후 CHECK_HOURS; 폐업/임시휴업이면 EXCLUDE, API 장애면 KEEP_UNKNOWN |
| 검토 필요 | 31 | NEEDS_REVIEW; 자동으로 확정/미매칭 취급하지 않음 |
| 미매칭 카페·맛집 | 4 | EXCLUDE |
| 미매칭 역사·문화·산책 | 11 | 현재 공식 운영시간이 미확인이므로 KEEP_UNKNOWN |
| 미매칭 쇼핑 | 4 | KEEP_UNKNOWN; 쇼핑 제외 여부는 팀 추천 정책에서 결정 가능 |

`KEEP_UNKNOWN`은 영업 중이라는 뜻이 아니다. 현재 JSON에 없는 카카오 ID도 `MAPPING_NOT_BUILT`로 반환한다. 실행 시 새 후보가 추가되면 매칭표를 보충해야 한다. 서버에서 자동으로 새 장소를 Google 검색하는 기능은 아직 연결하지 않았다.

## 5. 공식 운영정보 조사 결과

11곳 모두 현재 시설 전체 운영시간과 휴관일을 확정할 자료가 부족했다. `hours`와 `closedDays`는 `null`, `verificationStatus`는 `unconfirmed`다. **공식 운영시간 11건을 확보했다는 의미가 아니다.** 출처가 없는 항목은 출처를 지어내지 않고 빈 배열과 조사 질의를 기록했다.

| 조사 대상 | 카카오 ID | 판단 및 남은 확인 | 출처 기록 수 |
|---|---|---|---:|
| 구 통계국청사 | 568711784 | 건물의 일반 관광객 관람시간·휴관일을 확인하지 못함. 현재 입주 기관의 회원 이용시간을 관광 운영시간으로 사용하지 않음. | 1 |
| 박규수선생집터 | 10405648 | 헌법재판소 구내 집터. 정원 출입과 집터 접근 가능 범위를 별도 확인해야 하며 상시 개방으로 간주하지 않음. | 1 |
| 이상재집터 | 8043295 | 역사 표석의 현재 접근시간과 휴관일을 확인하지 못함. | 0 |
| 학아재미술관 | 27495541 | 2016년 안내의 11:00~19:00은 과거 정보임. 운영자 소개에 활동 중단 이력이 있어 현재 운영시간으로 사용하지 않음. | 2 |
| 한글전각미술관 | 27558021 | 검색에서 현재 시설의 공식 운영시간·정기휴관 안내를 찾지 못함. | 0 |
| 서울문화홍보원 | 1490573702 | 기관 발행 매체는 확인했으나 안국 지역 시설의 일반 관람시간과 휴관일은 확인하지 못함. | 1 |
| 예전사 | 9290049 | 동명 출판사 정보와 혼동 가능. 해당 문화시설의 공식 관람 안내를 찾지 못함. | 0 |
| 갤러리더플럭스 | 1479985456 | 사이아트의 전시공간으로 소개되지만 해당 공간의 현재 공식 운영시간·휴관일을 확정하지 못함. | 0 |
| 공평공원 | 919042739 | 공원 명칭이 행정 자료에 있으나 현재 개방시간·휴무 근거는 확인하지 못함. | 0 |
| 종로정원사마을 | 850231755 | 예약 프로그램 일정만 확인됨. 프로그램 시간을 공원 전체의 개방시간으로 사용하지 않음. 예약이 필요한 체험은 보고서의 예약 제외 범위에 해당. | 1 |
| 서린공원 | 735903470 | 도시계획 자료로 시설은 확인되나 현재 개방시간·휴관일은 확인하지 못함. | 1 |

학아재의 과거 11:00~19:00 안내는 `historicalHours`에 별도로 기록하고 추천용 사용을 금지했다. 종로정원사마을은 공식 예약 프로그램의 지정 날짜·시각을 `programOnly`로 분리했고, 이를 공원 전체 운영시간으로 적용하지 않는다. 자료와 확인 날짜는 [공식 정보 JSON](data/official-hours.json)에 있다.

## 6. 구현 파일과 전달 대상

| 파일 | 역할 | 전달 대상 |
|---|---|---|
| [match_places.py](match_places.py) | 카카오 수집·분류·구글 매칭·ID JSON 생성 | 정환 |
| [config.json](config.json) | 지역·테마·표본 상한·우선 장소 | 정환/종욱 |
| [place-mappings.json](data/place-mappings.json) | 실제 매칭·검토·미매칭 ID 90건 | 종욱 |
| [official-hours.json](data/official-hours.json) | 미매칭 시설의 공식 정보 조사 기록 | 종욱 |
| [GooglePlacesClient.java](java/src/main/java/kr/coursepick/places/GooglePlacesClient.java) | 영업시간·영업 상태·별점·평가 수 조회 | 종욱 |
| [PlaceLookupService.java](java/src/main/java/kr/coursepick/places/PlaceLookupService.java) | ID 연결·조회·오류/미매칭 처리 | 종욱 |
| [README.md](README.md) | 실행 방법·Java 통합 예시·상태 계약·출처 문서 | 팀 전체 |

Java는 JDK 17 이상과 Jackson 2를 사용하는 독립 모듈이다. HTTP 서버나 Spring Boot 프로젝트를 새로 만들지 않았다. 현재 프런트엔드 추천 화면에도 연결하지 않았다. 지도용 키 발급/제한/진현님 전달은 이번 코드 작업으로 완료 처리하지 않는다.

## 7. 검증 결과

| 검사 | 결과 | 한계 |
|---|---|---|
| Python 단위·데이터 검증 | 15개 통과 | 모의 사례와 현재 JSON 무결성 확인. 90곳의 실제 동일성 전체를 수동 검증한 것은 아님 |
| Java 컴파일 및 모의 HTTP 테스트 | 통과 | 정상 응답, 누락, 날짜 범위, 403/429/500, 404, 잘못된 JSON, ID 검증, 후보 처리 확인 |
| Java 실제 Google 조회 | 1건 성공 | 영업시간·별점 필드 존재 확인. 모든 매칭 ID의 영업정보를 확인한 것은 아님 |
| 비밀값 검사 | 소스/JSON/예제 설정에서 키 노출 없음 | .env, Java 빌드 출력, Python 캐시는 Git 제외 |
| 앱 TypeScript 검사 | 실패 | 기존 src/components/app-tabs.web.tsx:38 ref 타입 불일치 |
| 앱 lint | 실패 | 기존 src/hooks/use-color-scheme.web.ts:11 react-hooks/set-state-in-effect |

lint 실행을 위해 없던 ESLint 설정과 Expo 권장 개발 의존성을 추가했다. 위 두 앱 소스 오류는 장소 데이터 코드와 별개이며 이번 작업에서는 수정하지 않았다. 키 값은 출력하거나 저장 파일에 복사하지 않았다.

## 8. 다음 작업 순서

1. **검토 31건 확인:** 이름·지점·주소·좌표를 대조해 동일 장소이면 수동 매칭으로 확정한다. 경복궁 등 시연 핵심 장소부터 확인한다. 후보 Google ID는 원본 JSON의 `candidatePlaceIds`에 있다.
2. **공식 정보 11곳 보완:** 시설의 최신 공식 안내 또는 운영기관 확인을 확보한다. 확인되지 않으면 미확인 상태를 유지한다. 계절 시간, 입장 마감, 공휴일 대체 휴관도 확인한다.
3. **종욱님 서버에 통합:** Java 패키지와 두 JSON을 옮기고 환경 변수로 키를 주입한다. 추천 API와 장소 상세 API에서 조회 서비스를 호출한다.
4. **방문시간 검증 연결:** 도착부터 체류 종료까지 영업 구간에 포함되는지 검증한다. 휴게시간, 자정 넘김, 24시간 영업, 공식 휴관일을 처리한다. 7일 밖 날짜의 정규 영업시간을 확정 정보로 표시하지 않는다.
5. **앱 연결·통합 시연:** openStatus/출처·Google 표기·오류 화면을 연결하고, 기존 lint/타입 오류를 해결한 뒤 추천 전체 흐름을 테스트한다.

## 9. 전체 90건 목록

장소 원문을 영구 저장하지 않는 보고서 방침에 따라 ID 중심으로 정리했다. 카카오 ID 링크로 장소를 확인하고 Google 후보 ID는 원본 JSON에서 확인한다.

| 번호 | 카카오 ID | 테마 | 상태 | 확정 Google Place ID | 후보 ID 수 |
|---:|---|---|---|---|---:|
| 1 | [8465462](https://place.map.kakao.com/8465462) | 역사 | 자동 매칭 | ChIJy_KLudyifDUR8R3cB_PyJsU | 0 |
| 2 | [10771162](https://place.map.kakao.com/10771162) | 역사 | 자동 매칭 | ChIJ4wh0zluifDURaFBW2pdrKf8 | 0 |
| 3 | [18619553](https://place.map.kakao.com/18619553) | 역사 | 검토 필요 | - | 1 |
| 4 | [568711784](https://place.map.kakao.com/568711784) | 역사 | 미매칭 | - | 3 |
| 5 | [1253088572](https://place.map.kakao.com/1253088572) | 역사 | 자동 매칭 | ChIJGySdS32jfDURdLUkuiQ7WTs | 0 |
| 6 | [12765605](https://place.map.kakao.com/12765605) | 역사 | 자동 매칭 | ChIJDTb_pdyifDURjAwKUiWM02A | 0 |
| 7 | [8040331](https://place.map.kakao.com/8040331) | 역사 | 자동 매칭 | ChIJ7VvQlDOjfDURkExWAhlRIsA | 0 |
| 8 | [10405648](https://place.map.kakao.com/10405648) | 역사 | 미매칭 | - | 1 |
| 9 | [12559342](https://place.map.kakao.com/12559342) | 역사 | 검토 필요 | - | 5 |
| 10 | [8043295](https://place.map.kakao.com/8043295) | 역사 | 미매칭 | - | 2 |
| 11 | [8252251](https://place.map.kakao.com/8252251) | 역사 | 자동 매칭 | ChIJi_SJroWjfDUR0Vh5H3eWsl0 | 0 |
| 12 | [913934484](https://place.map.kakao.com/913934484) | 역사 | 검토 필요 | - | 1 |
| 13 | [8228024](https://place.map.kakao.com/8228024) | 역사 | 검토 필요 | - | 2 |
| 14 | [7888449](https://place.map.kakao.com/7888449) | 역사 | 자동 매칭 | ChIJCXue0cKifDURRmRoBF9st-E | 0 |
| 15 | [182424572](https://place.map.kakao.com/182424572) | 역사 | 자동 매칭 | ChIJuYs1ltyifDURvaRBrbHFkks | 0 |
| 16 | [1038056172](https://place.map.kakao.com/1038056172) | 전시·문화 | 자동 매칭 | ChIJY-nOhd2jfDUR_m1oGRl-0Cw | 0 |
| 17 | [17952661](https://place.map.kakao.com/17952661) | 전시·문화 | 자동 매칭 | ChIJRynTqcaifDURBaSFx8HncPs | 0 |
| 18 | [24268177](https://place.map.kakao.com/24268177) | 전시·문화 | 자동 매칭 | ChIJIQkfMcOifDUR9NPV-fas4ME | 0 |
| 19 | [11494384](https://place.map.kakao.com/11494384) | 전시·문화 | 자동 매칭 | ChIJF1exJcOifDUR0aW0kG8RqJk | 0 |
| 20 | [549419880](https://place.map.kakao.com/549419880) | 전시·문화 | 검토 필요 | - | 1 |
| 21 | [2108894844](https://place.map.kakao.com/2108894844) | 전시·문화 | 검토 필요 | - | 1 |
| 22 | [27495541](https://place.map.kakao.com/27495541) | 전시·문화 | 미매칭 | - | 2 |
| 23 | [27558021](https://place.map.kakao.com/27558021) | 전시·문화 | 미매칭 | - | 4 |
| 24 | [133825377](https://place.map.kakao.com/133825377) | 전시·문화 | 검토 필요 | - | 1 |
| 25 | [17574803](https://place.map.kakao.com/17574803) | 전시·문화 | 검토 필요 | - | 1 |
| 26 | [1490573702](https://place.map.kakao.com/1490573702) | 전시·문화 | 미매칭 | - | 5 |
| 27 | [9290049](https://place.map.kakao.com/9290049) | 전시·문화 | 미매칭 | - | 4 |
| 28 | [1422281685](https://place.map.kakao.com/1422281685) | 전시·문화 | 자동 매칭 | ChIJHVSEPQ2jfDURAVZgUb2umiY | 0 |
| 29 | [1058701476](https://place.map.kakao.com/1058701476) | 전시·문화 | 자동 매칭 | ChIJP1L5qJ2jfDURkDLcsM3KG4w | 0 |
| 30 | [1479985456](https://place.map.kakao.com/1479985456) | 전시·문화 | 미매칭 | - | 5 |
| 31 | [8625328](https://place.map.kakao.com/8625328) | 산책 | 자동 매칭 | ChIJt5HKlueifDURHONSIuFp_3A | 0 |
| 32 | [8035658](https://place.map.kakao.com/8035658) | 산책 | 검토 필요 | - | 1 |
| 33 | [18214024](https://place.map.kakao.com/18214024) | 산책 | 검토 필요 | - | 1 |
| 34 | [18213828](https://place.map.kakao.com/18213828) | 산책 | 자동 매칭 | ChIJfWSk9-mifDURhEvVOxxjhFA | 0 |
| 35 | [919042739](https://place.map.kakao.com/919042739) | 산책 | 미매칭 | - | 5 |
| 36 | [1248528572](https://place.map.kakao.com/1248528572) | 산책 | 검토 필요 | - | 5 |
| 37 | [11228160](https://place.map.kakao.com/11228160) | 산책 | 자동 매칭 | ChIJ3Rx0GI2jfDUR2P2ZpNH5ur4 | 0 |
| 38 | [850231755](https://place.map.kakao.com/850231755) | 산책 | 미매칭 | - | 5 |
| 39 | [1963402560](https://place.map.kakao.com/1963402560) | 산책 | 자동 매칭 | ChIJtdXF6-uifDURek7GXdGy_oI | 0 |
| 40 | [8181333](https://place.map.kakao.com/8181333) | 산책 | 검토 필요 | - | 5 |
| 41 | [735903470](https://place.map.kakao.com/735903470) | 산책 | 미매칭 | - | 5 |
| 42 | [7964572](https://place.map.kakao.com/7964572) | 산책 | 자동 매칭 | ChIJCVqKMeuifDURznNohrWIrd8 | 0 |
| 43 | [24974768](https://place.map.kakao.com/24974768) | 산책 | 검토 필요 | - | 1 |
| 44 | [8081471](https://place.map.kakao.com/8081471) | 산책 | 검토 필요 | - | 2 |
| 45 | [1462572933](https://place.map.kakao.com/1462572933) | 산책 | 검토 필요 | - | 5 |
| 46 | [414013653](https://place.map.kakao.com/414013653) | 쇼핑 | 자동 매칭 | ChIJE9ZfkYWjfDURAnRb_VOudDw | 0 |
| 47 | [572406137](https://place.map.kakao.com/572406137) | 쇼핑 | 자동 매칭 | ChIJz92FnPCjfDUR4zdSF5faTv4 | 0 |
| 48 | [1368678855](https://place.map.kakao.com/1368678855) | 쇼핑 | 자동 매칭 | ChIJjRPhTwCjfDURNv5Iio07sj8 | 0 |
| 49 | [792033514](https://place.map.kakao.com/792033514) | 쇼핑 | 자동 매칭 | ChIJU7MqqmijfDURELbbHuES6XM | 0 |
| 50 | [20849811](https://place.map.kakao.com/20849811) | 쇼핑 | 미매칭 | - | 1 |
| 51 | [1831441960](https://place.map.kakao.com/1831441960) | 쇼핑 | 미매칭 | - | 1 |
| 52 | [622017702](https://place.map.kakao.com/622017702) | 쇼핑 | 검토 필요 | - | 1 |
| 53 | [433161268](https://place.map.kakao.com/433161268) | 쇼핑 | 자동 매칭 | ChIJR4LGunSjfDURzmOzcckco6U | 0 |
| 54 | [1479060323](https://place.map.kakao.com/1479060323) | 쇼핑 | 자동 매칭 | ChIJS9Dt6tujfDUR5wE_KwdB65w | 0 |
| 55 | [574819073](https://place.map.kakao.com/574819073) | 쇼핑 | 검토 필요 | - | 1 |
| 56 | [1793274722](https://place.map.kakao.com/1793274722) | 쇼핑 | 미매칭 | - | 5 |
| 57 | [373844664](https://place.map.kakao.com/373844664) | 쇼핑 | 검토 필요 | - | 1 |
| 58 | [882729963](https://place.map.kakao.com/882729963) | 쇼핑 | 검토 필요 | - | 4 |
| 59 | [410399820](https://place.map.kakao.com/410399820) | 쇼핑 | 미매칭 | - | 1 |
| 60 | [20373895](https://place.map.kakao.com/20373895) | 쇼핑 | 자동 매칭 | ChIJz3DQ7sSifDURyYZa5LXo-J0 | 0 |
| 61 | [1928614414](https://place.map.kakao.com/1928614414) | 카페 | 검토 필요 | - | 5 |
| 62 | [928960375](https://place.map.kakao.com/928960375) | 카페 | 검토 필요 | - | 1 |
| 63 | [289647366](https://place.map.kakao.com/289647366) | 카페 | 자동 매칭 | ChIJD_la-WSjfDURwXpyrZ5JcUA | 0 |
| 64 | [11052973](https://place.map.kakao.com/11052973) | 카페 | 자동 매칭 | ChIJ-W_GUcOifDURNJqZfa9cpiE | 0 |
| 65 | [26936182](https://place.map.kakao.com/26936182) | 카페 | 자동 매칭 | ChIJcWMQPMOifDURQ0a44OboNuU | 0 |
| 66 | [1591136356](https://place.map.kakao.com/1591136356) | 카페 | 검토 필요 | - | 1 |
| 67 | [675148585](https://place.map.kakao.com/675148585) | 카페 | 검토 필요 | - | 1 |
| 68 | [16874884](https://place.map.kakao.com/16874884) | 카페 | 자동 매칭 | ChIJBVymacOifDURtXdPX5rjwr4 | 0 |
| 69 | [1653105633](https://place.map.kakao.com/1653105633) | 카페 | 검토 필요 | - | 1 |
| 70 | [390294975](https://place.map.kakao.com/390294975) | 카페 | 자동 매칭 | ChIJKZHRWcOifDURjqgza-B5yeE | 0 |
| 71 | [838446337](https://place.map.kakao.com/838446337) | 카페 | 자동 매칭 | ChIJx8Av-yWjfDURtcx3UCBY4Rw | 0 |
| 72 | [1432460444](https://place.map.kakao.com/1432460444) | 카페 | 미매칭 | - | 1 |
| 73 | [504361150](https://place.map.kakao.com/504361150) | 카페 | 미매칭 | - | 5 |
| 74 | [688039831](https://place.map.kakao.com/688039831) | 카페 | 미매칭 | - | 1 |
| 75 | [878756028](https://place.map.kakao.com/878756028) | 카페 | 검토 필요 | - | 2 |
| 76 | [648709154](https://place.map.kakao.com/648709154) | 맛집 | 자동 매칭 | ChIJrxtX7zajfDURlyGDMtGc0Jw | 0 |
| 77 | [1446956294](https://place.map.kakao.com/1446956294) | 맛집 | 검토 필요 | - | 1 |
| 78 | [27281766](https://place.map.kakao.com/27281766) | 맛집 | 자동 매칭 | ChIJJd0KFcOifDURpvVPdS2Bxh8 | 0 |
| 79 | [1741950525](https://place.map.kakao.com/1741950525) | 맛집 | 검토 필요 | - | 1 |
| 80 | [13096739](https://place.map.kakao.com/13096739) | 맛집 | 자동 매칭 | ChIJCWfsT8OifDURFbg_n_TIgMg | 0 |
| 81 | [1770495891](https://place.map.kakao.com/1770495891) | 맛집 | 자동 매칭 | ChIJ78K0whWjfDURzaYD6r34Alw | 0 |
| 82 | [474118494](https://place.map.kakao.com/474118494) | 맛집 | 검토 필요 | - | 5 |
| 83 | [171620390](https://place.map.kakao.com/171620390) | 맛집 | 미매칭 | - | 3 |
| 84 | [22837395](https://place.map.kakao.com/22837395) | 맛집 | 검토 필요 | - | 5 |
| 85 | [1368148550](https://place.map.kakao.com/1368148550) | 맛집 | 자동 매칭 | ChIJWSCNBACjfDURJ6hynmb6NOc | 0 |
| 86 | [2116027684](https://place.map.kakao.com/2116027684) | 맛집 | 자동 매칭 | ChIJA5eR8XWlfDURxPdNK82fY2A | 0 |
| 87 | [1806869446](https://place.map.kakao.com/1806869446) | 맛집 | 검토 필요 | - | 1 |
| 88 | [2143022811](https://place.map.kakao.com/2143022811) | 맛집 | 자동 매칭 | ChIJIYJ5QJajfDURHhmGhE-XxGQ | 0 |
| 89 | [1179567185](https://place.map.kakao.com/1179567185) | 맛집 | 자동 매칭 | ChIJsSwuQMKjfDURBQHRzZvq-i0 | 0 |
| 90 | [1125281223](https://place.map.kakao.com/1125281223) | 맛집 | 검토 필요 | - | 1 |
