# Weatherhood Server

기능 구현 전 서버 개발 기반이다. 업무 API·엔티티·테이블은 아직 없다. 기술 선택과 책임은 [서버 설계](docs/architecture.md), 공통 요구사항은 [스펙 레포](../weatherhood-specs/README.md)를 따른다.

## 환경

- JDK 25 (JAVA_HOME 설정)
- Docker 실행 환경과 Docker Compose
- Gradle 9.8.2는 Wrapper가 내려받는다.
- Spring Boot 4.1.2, springdoc 3.2.0, PostgreSQL 18.6을 사용한다. PostgreSQL 이미지는 로컬과 통합 테스트에서 동일하게 고정한다. 운영 버전·배포는 별도 설계한다.

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
- 업무 API가 없으므로 OpenAPI의 `paths`는 비어 있다.
- 중지: `docker compose down` (개발 데이터 볼륨은 유지)
- DB를 완전히 초기화할 때만: `docker compose down -v` (개발 데이터 삭제)

| 환경변수 | 기본값·설명 |
| --- | --- |
| WEATHER_DB_URL | jdbc:postgresql://localhost:5432/weatherhood |
| WEATHER_DB_USER | weatherhood |
| WEATHER_DB_PASSWORD | 필수. 기본 비밀번호 없음 |
| SERVER_PORT | 8080 |
| WEATHER_DB_NAME / WEATHER_DB_PORT | Compose의 DB 이름·호스트 포트. JDBC URL은 별도 설정 |

## 스키마

Flyway가 먼저 실행되고 JPA는 `validate`로 검증한다. `V1__initialize_database.sql`은 업무 테이블을 만들지 않고 초기 적용 이력만 남긴다. 기능별 DDL은 합의 후 V2부터 추가한다. 적용된 파일을 수정하지 않는다. 현재 엔티티가 없어 `validate`로 검증할 업무 매핑은 아직 없다.

## 테스트와 빌드

```sh
./gradlew test bootJar
```

Docker가 실행 중이어야 한다. 테스트에서는 `@ServiceConnection`이 임시 PostgreSQL 접속 정보를 제공하므로 `.env`나 개발 DB가 필요 없다. 테스트 DB는 개발용 Compose DB와 분리된다. Docker가 없으면 통합 테스트는 건너뛰지 않고 실패한다.

현재 테스트는 실제 PostgreSQL 접속, Flyway 이력과 검증, 업무 테이블·엔티티 부재, JPA 초기화, 실제 HTTP 서버의 OpenAPI·Swagger 응답을 확인한다. 테스트용 컨테이너는 Spring 테스트 컨텍스트가 관리하고 종료 시 정리한다. 테스트 사이에 데이터를 자동 삭제하는 설정은 아니며, 향후 데이터를 쓰는 테스트는 롤백 또는 명시적인 정리를 추가한다.

순수 로직이 생기면 DB 없이 단위 테스트한다. 현재는 업무 로직이 없어 형식적인 단위 테스트를 추가하지 않았다.

## 참고 및 검증

- Spring Initializr로 생성한 Gradle Wrapper와 Boot 의존성 관리를 사용한다. Wrapper 배포 파일 SHA-256을 고정했다.
- 2026-11-08: Java 25로 `./gradlew test bootJar` 통과. PostgreSQL 18.6 Testcontainers 통합 테스트 2개 통과.
- 별도 Compose 프로젝트와 포트(15432/18080)로 패키징 JAR을 기동해 PostgreSQL 18.6 연결, Flyway V1 적용, OpenAPI·Swagger HTTP 200을 확인했다. 검증용 서버·DB·볼륨은 정리했다.
- 개발 머신에는 IntelliJ와 독립된 사용자 표준 경로 `~/Library/Java/JavaVirtualMachines`에 JDK 25를 설치했다. `~/.zprofile`에서 `JAVA_HOME`을 설정하며 새 로그인 셸에 적용된다. 다른 개발 환경에서도 JDK 25와 `JAVA_HOME`을 준비한다.
- 엔티티·업무 테이블이 없어 기능별 쿼리와 스키마 매핑 검증은 기능 구현 시 추가한다.
- PostgreSQL 18.6 선택 근거: [공식 버전 정보](https://www.postgresql.org/versions.json).
