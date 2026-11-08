# 서버 레포 작업 안내

공통 스펙과 규칙은 통합 작업 공간의 `weatherhood-specs`에서 확인한다.

문서 작성·수정·리뷰 시 [공통 문서 책임 기준](../weatherhood-specs/rules/spec-documentation.md)을 따른다. 기능별 요구사항과 앱·서버 구현 설계는 스펙 레포의 `specs/<기능>/requirements.md`, `design.md`에 모은다. 앱 작업·테스트 계획은 `app-tasks.md`, 서버 작업·테스트 계획은 `server-tasks.md`에 나누고 API 계약은 `contracts/`를 참조한다.

이 레포의 `README.md`와 `docs/`는 서버 공통 기술 스택·구조 및 실행·테스트 실행·배포·운영을 설명한다. 기능별 DB·외부 연동·처리 설계를 중복 보관하지 않는다. 실제 코드·마이그레이션·자동 테스트는 이 레포에서 관리한다.
