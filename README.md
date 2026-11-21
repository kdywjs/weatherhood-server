# Weatherhood Server

오늘 날씨 기능을 구현 중인 서버다. 공통 응답·예외 처리와 역할별 패키지 구조는 적용했으며 실제 공급 연동과 운영 검증은 진행 중이다. 기술 선택과 책임은 [서버 설계](docs/architecture.md), 공통 요구사항은 [스펙 레포](../weatherhood-specs/README.md)를 따른다.

## 환경

- JDK 25 (JAVA_HOME 설정)
- Docker 실행 환경과 Docker Compose
- Gradle 9.8.2는 Wrapper가 내려받는다.
- Spring Boot 4.1.2, springdoc 3.2.0, PostgreSQL 18.6을 사용한다. PostgreSQL 이미지는 로컬과 통합 테스트에서 동일하게 고정한다. 운영 버전·배포는 별도 설계한다.

개발 테스트 서버의 미니 PC·GitHub Actions SSH 배포 구성은 [개발 테스트 서버 배포 설계](docs/dev-deployment.md)를 따른다. 도메인 연결과 실제 배포 설정은 후속 작업이다.

## 로컬 실행

```sh
cp .env.example .env
# .env의 개발용 비밀번호 등을 필요에 맞게 수정
set -a
. ./.env
set +a
docker compose up -d --wait
export WEATHER_DB_URL="jdbc:postgresql://localhost:${WEATHER_DB_PORT}/${WEATHER_DB_NAME}"
./gradlew bootRun
```

Compose는 `.env`를 읽지만 Spring Boot는 자동으로 읽지 않으므로 위처럼 셸에 변수를 내보낸다. `.env.example`의 비밀번호는 로컬 개발 전용 예시다. DB 포트를 바꾸면 JDBC URL도 맞춘다. DB는 127.0.0.1에만 노출된다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI: http://localhost:8080/v3/api-docs
- 오늘 날씨: `GET /api/v1/weather/today?regionId={id}`. 성공 payload는 공통 응답의 `data` 안에 있다.
- 중지: `docker compose down` (개발 데이터 볼륨은 유지)
- DB를 완전히 초기화할 때만: `docker compose down -v` (개발 데이터 삭제)

| 환경변수 | 기본값·설명 |
| --- | --- |
| WEATHER_DB_URL | jdbc:postgresql://localhost:5432/weatherhood |
| WEATHER_DB_USER | weatherhood |
| WEATHER_DB_PASSWORD | 필수. 기본 비밀번호 없음 |
| SERVER_PORT | 8080 |
| KMA_API_HUB_KEY | 기상청 API허브 인증키 |
| AIRKOREA_SERVICE_KEY | 에어코리아 공공데이터포털 서비스 키 |
| KASI_SERVICE_KEY | 한국천문연구원 출몰시각 정보 서비스 키. 별도 활용 승인 필요 |
| WEATHER_DB_NAME / WEATHER_DB_PORT | Compose의 DB 이름·호스트 포트. JDBC URL은 별도 설정 |

공급처 키는 Git에서 제외한 `.env`에 설정한다. 일출·일몰의 저장·재조회 기준과 외부 API 계약은 [오늘 날씨 설계](../weatherhood-specs/specs/today-weather/design.md#일출일몰-연동과-저장)를 따른다. KASI의 일일 호출 상한은 `weather.kasi-daily-limit`(초기값 10000)으로 설정한다.

## Apple 로그인과 지역 API 실행

[인증 API 계약](../weatherhood-specs/contracts/auth-api.md)과 [지역 API 계약](../weatherhood-specs/contracts/region-api.md)이 원본이다. 날씨와 공개 지역 조회는 키 설정 없이도 기동할 수 있으나, Apple 설정이 없으면 로그인은 503으로 실패한다. 카카오 키가 없으면 좌표 변환만 503이다.

| 환경변수 | 설명 |
| --- | --- |
| APPLE_CLIENT_ID | native iOS Bundle ID. 기본값 co.kr.ansandy.weatherhood |
| APPLE_TEAM_ID / APPLE_KEY_ID | Apple Developer의 팀·전용 키 식별자 |
| APPLE_PRIVATE_KEY_PATH | 서버에서 읽을 수 있는 Git 외부 .p8 절대 경로 |
| AUTH_TOKEN_ENCRYPTION_KEY | 암호학적 난수 32바이트의 Base64. Apple refresh token 암호화용 |
| KAKAO_REST_API_KEY | 카카오 로컬 API용 REST 키 |

현재 개발 머신의 인증 설정은 `~/.config/weatherhood/auth.env`(권한 600), Apple 키는 같은 보호 폴더의 `apple/`에 둔다. 로컬 실행 시 기존 `.env` 다음에 설정을 읽는다. 비밀값을 터미널에 출력하지 않는다.

```sh
set -a
. ./.env
. ~/.config/weatherhood/auth.env
set +a
./gradlew bootRun
```

컨테이너에서는 .p8을 읽기 전용 secret 파일로 마운트하고 경로와 암호화 키를 실행 환경에 공급한다. 이미지·GitHub 로그·문서에 키를 넣지 않는다. 암호화 키를 바꾸려면 기존 암호문 재암호화가 필요하므로 단순 교체하지 않는다. 개발 테스트 서버의 `/opt/weatherhood-dev/.env`에 인증 환경변수를 공급하고 `.p8`은 `/opt/weatherhood-dev/secrets/apple-signin.p8`에서 컨테이너 `/run/secrets/apple-signin.p8`으로 읽기 전용 마운트한다. 키는 컨테이너 UID 10001 소유·권한 400이다.

Swagger의 Authorize에 **Weatherhood 세션 토큰**을 입력한다. NPM 문서용 Basic Auth와 별개이며 Apple identity token을 개인 API Bearer로 사용하지 않는다. dev 문서 출처의 GET·POST·PUT·DELETE만 CORS로 허용하고 쿠키는 사용하지 않는다.

## 스키마

Flyway가 먼저 실행되고 JPA는 `validate`로 검증한다. `V1__initialize_database.sql`은 업무 테이블을 만들지 않고 초기 적용 이력만 남긴다. 기능별 DDL은 후속 버전으로 추가한다. 적용된 파일을 수정하지 않는다. V2~V4에는 날씨 저장·행정동 적재·호출량 관리 테이블이 있고 V5에는 계정·세션·challenge·저장 지역이 있다. JPA 매핑을 검증한다.

## 테스트와 빌드

```sh
./gradlew test bootJar
```

Docker가 실행 중이어야 한다. 테스트에서는 `@ServiceConnection`이 임시 PostgreSQL 접속 정보를 제공하므로 `.env`나 개발 DB가 필요 없다. 테스트 DB는 개발용 Compose DB와 분리된다. Docker가 없으면 통합 테스트는 건너뛰지 않고 실패한다.

현재 테스트는 실제 PostgreSQL·Flyway·JPA 초기화, 전국 지역 적재, 발표 시각에 따른 저장 갱신, 날씨 선택·유효성·정규화, 공통 응답과 HTTP 오류, OpenAPI·Swagger 응답을 확인한다. 테스트용 컨테이너는 Spring 테스트 컨텍스트가 관리하고 종료 시 정리한다. HTTP 통합 테스트가 만든 지역은 테스트 종료 후 명시적으로 삭제한다. 그 외 테스트 데이터는 독립된 식별자를 사용하며, 새 테스트도 실행 순서에 의존하지 않도록 정리한다.

시간 경계·자료 선택·강수량·미세먼지 정규화는 DB 없이 단위 테스트한다. HTTP 통합 테스트의 외부 공급처는 테스트 대역이며 실제 공급처 호출 검증을 대신하지 않는다.

## 참고 및 검증

- Spring Initializr로 생성한 Gradle Wrapper와 Boot 의존성 관리를 사용한다. Wrapper 배포 파일 SHA-256을 고정했다.
- 2026-11-08: Java 25로 `./gradlew test bootJar` 통과. PostgreSQL 18.6 Testcontainers 통합 테스트 2개 통과.
- 별도 Compose 프로젝트와 포트(15432/18080)로 패키징 JAR을 기동해 PostgreSQL 18.6 연결, Flyway V1 적용, OpenAPI·Swagger HTTP 200을 확인했다. 검증용 서버·DB·볼륨은 정리했다.
- 개발 머신에는 IntelliJ와 독립된 사용자 표준 경로 `~/Library/Java/JavaVirtualMachines`에 JDK 25를 설치했다. `~/.zprofile`에서 `JAVA_HOME`을 설정하며 새 로그인 셸에 적용된다. 다른 개발 환경에서도 JDK 25와 `JAVA_HOME`을 준비한다.
- PostgreSQL 18.6 선택 근거: [공식 버전 정보](https://www.postgresql.org/versions.json).

- 2026-11-15 가독성 리팩토링: `WeatherTypes`의 타입을 역할별 파일로 분리하고, 수집 요청 계획·대기·저장, 외부 요청 생성·응답 파싱, 항목별 정규화를 메서드로 분리했다. 현재 API 경로와 공통 응답 계약은 유지한다.
- 2026-11-15 현재 구현: `./gradlew test bootJar javadoc --console=plain` 통과. 테스트 63개, 실패·오류·건너뜀 0개. Javadoc은 생성됐으나 단순 접근자 등의 주석·태그 누락 경고 100개가 남아 있다.
- 공급 종류별 PTY 코드, 강수량 결측·숫자/범위·null 구분, 자정 구간, 오후 첫 요청의 과거 예보·하루 최고최저 유지, 관측 항목별 누락 판단, 중복 요청 후보 제거와 재시도 대기를 검증했다. 공급 항목 정규화부터 DB 저장·HTTP 반환·재조회까지 통합 테스트했다.
- 일출·일몰은 실제 DB 저장·재조회·당일 호출 억제·날짜 변경·부분 응답 보충을 HTTP 통합 테스트했다. XML 날짜·시각 검증, 외부 엔티티 차단, 인증 오류 처리도 확인했다.
- 2026-11-15 실제 `KasiSunTimesClient` 인증 호출에서 서울 기준 일출 07:11, 일몰 17:22를 정상 수신했다. 이는 단일 위치 표본이며 전국 검증을 대신하지 않는다.
- 외부 API는 자동 테스트에서 대역을 사용한다. 실제 인증 표본 조회와 공식 근거는 [기능 설계](../weatherhood-specs/specs/today-weather/design.md#참고-및-검증)를 따른다. 전국·악천후 전 유형·부하·동시 수집 전체 검증은 완료하지 않았다.

- 2026-11-21 Apple 로그인·지역 서버 구현: 실제 RSA/ES256 서명과 HTTP 공급처 대역, PostgreSQL 18.6을 사용해 로그인·30일 만료·재사용 차단·로그아웃·탈퇴·격리·동시 추가 제한·순서·기본 지역·검색·좌표 매핑을 검증했다. `./gradlew test bootJar javadoc --console=plain`에서 테스트 88개 통과(실패·오류·건너뜀 0), 실행 JAR·Javadoc 생성 성공. Javadoc의 주석·태그 누락 경고는 0개다. 변경 Java 파일 78개에 프로젝트 XML을 이용한 IntelliJ CLI 포매터를 적용하고 120자·wildcard import·diff 공백을 확인했다.
- 카카오 공식 예제 좌표 37.4012191, 127.1086228을 실제 인증 조회해 H 코드 4113565500을 받았고 기존 삼평동 ID 51bf94b3-e51d-5ef7-b00f-45769ea0e8cb와 연결됨을 확인했다. 이는 단일 표본이며 전국 경계 검증을 의미하지 않는다.
- Apple 앱 ID·전용 키 등록과 로컬 보관은 완료했다. 실제 사용자 Apple 코드 교환·iOS 로그인은 아직 수행하지 않았다. 개발 테스트 서버 배포 결과는 GitHub Actions 실행과 배포 설계를 참조한다.
