# Weatherhood Server

Java 21, Spring Boot 4.1.2(Spring Framework 7), Gradle Wrapper 9.5.1로 실행하는 날씨 서버다. 공통 요구사항·계약의 원본은 인접한 `weatherhood-specs` 레포다.

## 실행

```powershell
$env:KMA_API_KEY = '<기상청 API허브에서 발급한 키>'
.\gradlew.bat bootRun
```

macOS/Linux는 `./gradlew bootRun`을 사용한다. 인증키는 API허브의 `authKey`이며 공공데이터포털 `serviceKey`와 혼용하지 않는다. 원문 키를 설정하면 요청 시 한 번 인코딩한다. `.env.example`은 설정 예시이고 `.env`를 자동으로 읽지 않는다.

키 없이 서버를 실행할 수 있지만 사용할 저장 자료도 없으면 날씨 API는 503을 반환한다. 가상 날씨를 정상 응답으로 제공하는 실행 모드는 없다. 자동 테스트에서만 합성 공급처를 사용한다.

기본 로컬 DB는 `./data/weatherhood.mv.db`인 H2 파일 저장소다. 재실행 후 자료와 호출 예산을 유지한다. `WEATHER_DB_URL`, `WEATHER_DB_USER`, `WEATHER_DB_PASSWORD`로 로컬 접속 설정을 바꿀 수 있다. 운영 DB 선택과 운영 배포는 아직 범위 밖이며 현재 SQL은 H2 기준이다.

## 조회

```text
GET http://localhost:8080/v1/weather?lat=37.5665&lon=126.978
GET http://localhost:8080/v1/weather/timeline?lat=37.5665&lon=126.978&date=2026-11-02
```

`date`에는 실제 한국 현지 날짜를 사용한다. 타임라인의 첫 지원 범위는 오늘부터 실제 시간별 자료가 있는 날짜이며 최대 오늘+4일이다. 없는 구간은 패딩하지 않는다. 모든 시각은 `+09:00`, 기본 단위는 ℃·m/s·mm·%다. 드래그·재생은 응답을 앱에 보관해 처리한다.

요약에는 최신 제공 실황을 우선하는 `current`, 현재 시간 구간부터 다음 24시간 범위의 `hourly`, 공급처 최고/최저와 시간대별 상태를 가진 `daily`를 제공한다. 해상도와 경계에 따라 `hourly` 점 개수는 고정 24개가 아니다. 대표 일별 날씨 집계는 미정이므로 `sky`, `precipitationType`은 null과 `aggregation_not_defined`를 반환하고 개별 `periods`를 보존한다. 단순 시간별 극값을 하루 최고/최저로 대체하지 않는다.

## 객체의 책임과 4레이어

| 레이어 | 객체 | 책임 |
| --- | --- | --- |
| 표현 `presentation` | `WeatherController`, `WeatherResponse`, `WeatherErrorHandler` | HTTP 입력·DTO 변환·오류 응답. 조회와 조합 규칙은 가지지 않음 |
| 응용 `application` | `WeatherQueryService`, `WeatherCollector`, `WeatherGateway` | 조회 흐름·수집 작업 조율, 같은 수집 키의 동시 작업 합치기 |
| 도메인 `domain` | `Grid`, `WeatherSource`, `WeatherTimeline`, `WeatherOverview`, `WeatherBatch`, `WeatherValues` | 격자·발표 시각·우선순위·출처·기간·결측 판단. Spring/JDBC/HTTP에 의존하지 않음 |
| 인프라 `infrastructure` | `KmaWeatherGateway`, `KmaPayloadParser`, `JdbcWeatherRepository`, `KmaRequestBudget`, `KmaSupportedLocations` | 외부 HTTP·파싱·공식 지원 목록·파일 DB·호출 예산 |

DTO는 독립된 레이어가 아니라 표현 경계의 자료다. Service는 도메인에게 조합을 요청하고 Repository/Gateway를 통해 자료를 확보한다. Repository와 Gateway의 인터페이스는 사용하는 쪽에 두고 구현체만 인프라에 둔다. 불필요한 Service 인터페이스·기반 CRUD·범용 추상 Repository는 만들지 않는다.

## 수집과 개발 기본값

첫 요청은 최신 실황·초단기·단기와 전날 23시 단기예보, 오늘 00시부터의 실황을 조회한다. 같은 격자·공급 종류·발표 시각을 재사용하고 전체 페이지 검증 후에만 저장한다. 실제 소급 조회가 거부되는 경우 예보 보충 또는 누락으로 표시한다. 이 경로가 실제 기상청에서 오늘 00시를 복구하는지는 인증키로 검증해야 한다.

최근 요청한 격자는 주기적으로 새 발표를 확인한다. 저장 자료는 최소 48시간 보존하고 아직 유효한 미래 예보도 삭제하지 않는다. 초기 구현의 수집 합치기는 단일 서버 인스턴스 범위다.

다음 값은 실행 가능한 로컬 검증용 기본값이며 운영 정책 확정값이 아니다. 공식 제공 지연·계정 한도와 실제 응답 검증 후 공통 스펙과 함께 조정한다. `--weather.collection.stale-tolerance=PT2H` 같은 Spring 실행 인자로 명시적으로 바꿀 수 있다.

| 설정 | 개발 기본값 |
| --- | --- |
| 외부 HTTP 전체 응답 시간 | 3초/호출, 본문 포함 |
| 응답 크기 | 2MiB/호출, 초과 시 수신 취소 |
| 동시 외부 수집 | 2개 |
| 동시 대기 작업 | 최대 128개, 용량 초과 시 저장 자료만 사용 |
| 요청의 수집 대기 | 최대 20초. 이미 시작한 페이지 수집은 별도 HTTP 제한 아래 마무리됨 |
| 실패한 같은 자료 재시도 간격 | 1분 |
| 최근 지역 유지 / 갱신 확인 | 1시간 / 1분 |
| 오래된 예보 허용 | 0초. 명시적으로 허용하기 전 오래된 미래 예보를 제공하지 않음 |
| 일일 외부 호출 / 용량 예산 | 19,000회 / 4.5GB |

호출·용량 예산은 같은 DB를 이용하는 이 서버의 요청을 센다. 같은 API 키로 다른 프로그램에서 사용한 양은 알 수 없다. 페이지마다 호출을 집계하고 응답 크기를 예약한 후 실제 수신량으로 조정한다. 실패로 수신량을 알 수 없으면 예약량을 보수적으로 유지한다.

## 공식 지역 목록

지원 범위는 기상청 공식 `동네예보지점좌표(위경도)_260701.xlsx`의 `kor` 행에서 중복을 제거한 1,632개 행정구역 대표 격자다. `src/main/resources/kma-supported-grids.csv`에 포함한다. 이 목록은 전국 육지 전체나 정확한 국가 경계 도형이 아니므로 목록에 없는 국내 산악·해상 격자는 422가 될 수 있다. 단순 사각형만으로 국내 위치를 허용하지 않는다.

원본: [기상청 동네예보 지점 좌표](https://apihub.kma.go.kr/getAttachFile.do?fileName=%EB%8F%99%EB%84%A4%EC%98%88%EB%B3%B4%EC%A7%80%EC%A0%90%EC%A2%8C%ED%91%9C%28%EC%9C%84%EA%B2%BD%EB%8F%84%29_260701.xlsx).
원본 SHA-256: `746b5e5be10430106abccc795a16c16d0f1fd0f081e8ab2765d0adc983a003c1`.
출처: 기상청. API 데이터의 이용조건과 출처 표시는 각 서비스의 공공누리 조건을 따른다.

## 검증

```powershell
.\gradlew.bat test bootJar
```

시나리오 원본: [지역별 날씨 테스트](docs/test/weather-data-test-scenarios.md). 합성 기상청 JSON, 로컬 HTTP 공급처, 고정 시계, 실제 Spring HTTP 서버와 H2를 사용한다. 외부 키를 요구하지 않는다.

실제 기상청의 소급 조회·제공 지연·정성 코드·계정 권한은 아직 인증 조회로 검증하지 않았다. 중기예보, 대기질·자외선·일출일몰·평년 비교의 공급 연결, 앱 연결과 운영 환경 검증은 남아 있다. 첫 검증 목표인 실제 한 지역의 현재 날씨·오늘 00시 자료 제공을 합성 테스트만으로 완료 처리하지 않는다.
