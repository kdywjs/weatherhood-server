# 서버 레포 작업 안내

공통 스펙과 규칙은 통합 작업 공간의 `weatherhood-specs`에서 확인한다.

문서 작성·수정·리뷰 시 [공통 문서 책임 기준](../weatherhood-specs/rules/spec-documentation.md)을 따른다. 기능별 요구사항과 앱·서버 구현 설계는 스펙 레포의 `specs/<기능>/requirements.md`, `design.md`에 모은다. 앱 작업·테스트 계획은 `app-tasks.md`, 서버 작업·테스트 계획은 `server-tasks.md`에 나누고 API 계약은 `contracts/`를 참조한다.

이 레포의 `README.md`와 `docs/`는 서버 공통 기술 스택·구조 및 실행·테스트 실행·배포·운영을 설명한다. 기능별 DB·외부 연동·처리 설계를 중복 보관하지 않는다. 실제 코드·마이그레이션·자동 테스트는 이 레포에서 관리한다.

## Java 작성 규칙

Java 소스를 작성·수정·리뷰하기 전에 [Java 작성 규칙](docs/java-style.md)을 읽고 따른다. 우아한테크코스 Java 스타일을 기본으로 하고, 별도 변경이 없는 항목은 Google Java Style을 따른다. 프로젝트의 `.idea/codeStyles/`와 `.editorconfig`에 맞춘다.

한 줄에 여러 문장·필드·메서드를 몰아 쓰거나 wildcard import를 사용하지 않는다. 새 코드와 수정한 코드에는 공백 4칸, 줄 길이 120자, 필수 중괄호, 의미 있는 이름과 적절한 줄바꿈을 적용한다. 구현 편의를 위해 이 기준을 생략하지 않는다. 스타일 적용과 자동 테스트 통과 여부는 구분해서 보고한다.

컨트롤러용 DTO는 요청에 `Request`, 응답에 `Response` 접미사를 붙인다. 서비스·외부 API·저장용 타입에는 이 접미사를 일괄 적용하지 않는다.

메서드는 Java 작성 규칙의 동작·대상 기준을 따르며, 업무 메서드에 대상 없는 `get` 등을 사용하지 않는다. record 접근자·팩터리·프레임워크 관례는 유지한다.
