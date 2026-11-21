# 서버 기술 및 내부 설계

## 범위와 상태

서버 기술 선택의 원본이다. 기능 요구사항과 API 계약은 [공통 스펙](../../weatherhood-specs/README.md)을 따른다. 서버 구현은 초기화되었으며 아래 내용은 합의된 설계다. 기본 프로젝트·DB 연결·테스트 환경을 구성했다. 실행 방법과 검증 범위는 [서버 README](../README.md)를 따른다. 오늘 날씨 기능은 구현 중이며 공통 응답·예외 처리와 역할별 패키지를 적용했다. 기능 전체 완료와 운영 검증을 의미하지 않는다.

## 기술 선택

| 항목 | 결정과 이유 |
| --- | --- |
| 언어·프레임워크 | Java 25 LTS · Spring Boot 4.1.2를 기준안으로 사용한다. |
| 빌드 | Gradle 9.8.2 + Wrapper. Spring Initializr가 제공한 버전으로 고정한다. |
| 실행·배포 | 하나의 Spring Boot 애플리케이션에서 API와 날씨 수집을 함께 실행한다. |
| 데이터베이스 | PostgreSQL. 지역·시간 기반 날씨 조회와 관계형 데이터 관리에 적합하고 기존 사용 경험을 활용할 수 있다. |
| 데이터 접근 | Spring Data JPA. JDBC 병행은 대량 저장이나 성능 문제를 확인한 뒤 검토한다. |
| 스키마 변경 | Flyway로 버전별 SQL을 Git에서 관리한다. 이미 적용한 변경 파일은 수정하지 않고 새 파일을 추가한다. JPA의 `ddl-auto`는 `validate`를 사용한다. |
| API 문서 | springdoc-openapi + Swagger UI. API 계약을 바탕으로 실제 직렬화 결과와 문서를 일치시킨다. |
| DB 통합 테스트 | Testcontainers로 개발 DB와 분리한 PostgreSQL 컨테이너를 실행하고 Flyway 적용 후 JPA 동작을 검증한다. |
| 순수 로직 테스트 | Spring·DB 없이 핵심 규칙을 검증한다. |

PostGIS·TimescaleDB는 도입하지 않는다. 공간 검색·대규모 시계열 처리 요구가 생기면 검토한다. Redis·메시지 큐·배치 프레임워크도 현재 선택하지 않았다.

## 개발 테스트 서버 배포

Ubuntu 미니 PC에 Docker Compose로 개발 테스트 서버를 구성하고 GitHub 제공 러너에서 SSH로 배포한다. 외부 HTTPS는 기존 Nginx Proxy Manager로 제공하며 Cloudflare Tunnel은 사용하지 않는다. GitHub Actions·GHCR·SSH 배포 흐름, 환경 분리, 복구 기준과 미정 사항은 [배포 설계](dev-deployment.md)가 원본이다. 실제 운영(production) 환경은 별도 결정하며, 테스트 서버 구축 완료를 의미하지 않는다.

## 코드 구조와 책임

기능별 패키지 안에 일반적인 Controller·Service·Repository·Entity·DTO 구조를 사용한다. Port·Adapter나 별도 policy 계층을 만들지 않는다.

```text
src/main/java/com/weatherhood/
├── WeatherhoodServerApplication.java
├── common/
│   ├── config/       # 공통 Bean 설정
│   ├── exception/    # ErrorCode, ApplicationException, GlobalExceptionHandler
│   └── response/     # ApiResponse<T>
└── today/
    ├── controller/   # HTTP 입력 검증, 상태·헤더, 응답 DTO 변환
    ├── dto/          # 요청·응답 및 서비스/외부 연동 데이터 전달 타입
    ├── service/      # 업무 흐름, 수집, 응답 조합, 갱신 판단
    ├── entity/       # JPA 영속성 엔티티
    ├── repository/   # Spring Data JPA 및 저장·조회 구현
    └── client/       # 외부 API 호출·공급 응답 정규화
```

- `auth/`와 `region/`도 같은 역할별 패키지를 사용한다. `auth/config`는 인증 필터와 SecurityFilterChain을 담당한다.
- 기본 흐름은 Controller → Service → Repository 또는 외부 API Client다. Repository와 Client는 Controller나 Service를 참조하지 않는다.
- 컨트롤러용 요청·응답 DTO는 각각 `Request`·`Response` 접미사를 사용한다. 내부 처리용 타입에는 이 규칙을 적용하지 않는다.
- Controller는 Entity를 직접 반환하지 않는다. 응답 전용 DTO를 `ResponseEntity<ApiResponse<T>>`로 반환한다. Service는 `ResponseEntity`와 HTTP 상태를 알지 않는다.
- Service는 필요한 책임별 클래스로 나눈다. 날짜·유효성·우선순위 판단은 Spring·DB 없이 검증 가능한 클래스로 유지하되 별도 policy 패키지를 두지 않는다.
- Entity는 DB 매핑이며 DTO와 분리한다. 모든 Service에 인터페이스를 만들지 않는다. 외부 호출 경계의 인터페이스는 테스트 대체가 필요한 경우에만 사용한다.
- 공통 기능은 실제 여러 기능에 적용할 응답·예외·설정만 둔다. 기능별 규칙을 common으로 옮기지 않는다.
- `src/main/resources/db/migration`은 Flyway DDL, `src/test/java`는 자동 테스트, `scripts/`는 데이터 준비 도구, `docs/`는 공통 구조·운영 문서다. 비밀값은 Git에서 제외한 `.env` 또는 실행 환경변수로 제공한다.

## 공통 응답과 예외 처리

- 성공은 `ApiResponse.success(dto)`, 실패는 공통 처리기의 `ApiResponse.failure(...)`로 생성한다. JSON은 성공 시 `data`와 `error: null`, 실패 시 `data: null`과 `error`를 갖는다. JSON 계약 원본은 [오늘 날씨 계약](../../weatherhood-specs/contracts/today-weather-api.md)이다.
- `ErrorCode`는 안정적인 외부 코드 문자열과 공개 메시지를 갖는다. enum 이름과 응답 코드는 분리하며 `name()`을 응답 코드로 사용하지 않는다.
- 예상 가능한 업무 실패는 `ApplicationException`으로 전달한다. HTTP 상태·Retry-After 헤더 매핑은 `GlobalExceptionHandler`가 담당한다. 공급처 실패는 Client에서 `WeatherProviderException`으로 변환하고 Service가 부분 응답 여부를 판단한다. 비동기 수집의 내부 실패는 `WeatherCollectionException`으로 전달하며 자료 부족으로 숨기지 않는다. 숫자·날짜·통신·JSON 파싱은 구체적인 예외만 처리하고, 예상하지 못한 예외의 최종 500 처리는 공통 처리기에 둔다.
- 요청 검증 실패는 400, 존재하지 않는 경로는 404, 지원하지 않는 메서드는 405로 처리한다. 업무 오류는 계약의 상태를 유지하며 예기치 못한 실패만 500이다. 실패를 HTTP 200으로 감싸지 않는다.
- 예외 메시지·원본 공급 응답·인증키·정확한 GPS·쿼리 문자열을 오류 응답이나 일반 로그에 기록하지 않는다. 예상하지 못한 오류 로그는 예외 타입을 남긴다. 추적 ID·관측 도구 도입은 별도 설계한다.

## 테스트 데이터와 환경

Testcontainers의 임시 컨테이너와 데이터는 수명 종료 시 정리한다. 컨테이너를 여러 테스트에서 공유할 경우 테스트 사이의 데이터는 자동 초기화되지 않으므로 롤백·삭제 등으로 격리한다. 컨테이너 공유 범위와 초기화 방법은 테스트 구성 시 정한다.

로컬 DB는 Docker Compose의 PostgreSQL 18.6으로 실행하며 통합 테스트도 같은 이미지 버전을 사용한다. 운영 DB 버전·환경은 별도 결정한다. 인증 경계는 Spring Security를 사용하며 기능별 토큰·Apple 연동·DB 정책은 [인증 설계](../../weatherhood-specs/specs/authentication/design.md)를 따른다. 공급처 수집·재시도·보관 구현과 운영 수치는 기능 설계·공급처 검증 후 정한다.

## 참고 및 검증

- 근거: 서버 재설계 대화에서 합의한 기술 선택과 책임 분리, 문서 관리 기준.
- 기본 환경의 검증 결과는 [서버 README](../README.md)에 기록한다. 초기화 이전 서버의 테스트 기록은 새 설계의 검증 증적으로 사용하지 않는다.
