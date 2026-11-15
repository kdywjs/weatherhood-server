# Java 소스 작성 규칙

[우아한테크코스 Java Style Guide](https://github.com/woowacourse/woowacourse-docs/blob/main/styleguide/java/README.md)를 따른다. 별도 변경 사항이 없는 항목은 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)를 따른다. 우아한테크코스의 공개 규칙이며 우아한형제들 전사 내부 규칙으로 간주하지 않는다.

## 작성 기준

- 블록 들여쓰기는 공백 4칸, 이어지는 줄은 최소 8칸, 줄 길이는 원칙적으로 120자다. 긴 URL 등 원본 가이드의 예외를 허용한다.
- 한 줄에 문장 하나만 쓴다. 변수 선언을 한 줄에 여러 개 모으지 않는다.
- 조건문·반복문의 중괄호를 생략하지 않는다. 메서드 사이와 논리적인 처리 구간에는 적절한 빈 줄을 둔다.
- 일반 import와 static import 모두 wildcard(`*`)를 사용하지 않는다. static import 그룹을 먼저 두고 빈 줄 뒤 일반 import를 정렬한다.
- 클래스는 UpperCamelCase, 메서드·변수는 lowerCamelCase, 상수는 UPPER_SNAKE_CASE를 사용한다. 공개 메서드의 한 글자 매개변수는 피한다.
- 파일에는 최상위 타입 하나를 두고 파일 이름은 해당 타입 이름과 맞춘다. 관련된 중첩 타입까지 일률적으로 금지하는 것은 아니다.
- UTF-8·LF를 사용하고 파일 마지막에 줄바꿈을 둔다.

포매터로 역할 분리나 이름의 의미까지 검증할 수는 없다. 의미 있는 이름, 메서드의 책임, 복잡한 조건과 중첩은 코드 리뷰에서 함께 확인한다. 교육 미션의 메서드 15줄 제한·else 금지 등을 별도 합의 없이 프로젝트 규칙으로 추가하지 않는다.

## 메서드 이름

메서드는 lowerCamelCase로 `동작 + 대상 + 필요한 조건`을 표현한다. 같은 책임에는 같은 동사를 쓴다.

| 동작 | 기준과 예 |
| --- | --- |
| `get` | 필요한 업무 결과 조회: `getTodayWeather`. 갱신 수집을 포함하는지는 Javadoc에 명시한다. |
| `find` | 없을 수 있는 값 검색: `findNextAttemptTime`. 부재 표현은 반환 타입·계약으로 명시한다. |
| `fetch` / `collect` | 외부 호출 `fetchWeatherData` / 수집 조율 `collectRequiredWeatherData` |
| `save` / `update` | 저장 `saveWeatherData` / 조건부 변경 `updateForecastIfNewer` |
| `calculate` / `compose` | 계산 `calculateNextCheckTime` / 자료 조합 `composeTodayWeather` |
| `parse` / `normalize` | 표현 파싱 `parseProviderDateTime` / 공급 자료 정규화 `normalizeKmaItems` |
| `is` / `has` / `can` | 상태 판정 `isUsable`, `hasNoWeatherData`. 상태를 변경하는 시도는 `tryReserveProviderCall`처럼 드러낸다. |
| `validate` | 유효하지 않으면 예외를 던지는 검증. boolean 판정과 구분한다. |

- 업무 메서드에 대상 없는 `get`, `process`, `handle`, `execute`를 사용하지 않는다.
- 시각·기간·단위가 혼동되면 `Time`, `Seconds`, `Millis` 등을 명시한다.
- 긴 이름의 `And` 나열은 책임 분리를 검토한다. 단어 자체를 일률적으로 금지하지 않는다.
- record 접근자, `from`·`of` 팩터리, 프레임워크 메서드와 오버라이드·라이프사이클 메서드는 관례를 유지한다.

## 컨트롤러 DTO 이름

- 컨트롤러용 요청 DTO는 `Request`, 응답 DTO는 `Response` 접미사를 사용한다. 예: `TodayWeatherRequest`, `TodayWeatherResponse`.
- 서비스 처리 결과·외부 공급처 연동·저장용 전달 객체에 이 규칙을 일괄 적용하지 않는다. 해당 책임을 나타내는 이름을 사용한다.
- 쿼리 파라미터를 직접 받는 API에 이름 규칙을 맞추기 위한 불필요한 요청 DTO를 만들지 않는다. 요청 DTO를 도입하면 위 규칙을 적용한다.

## Javadoc

- 클래스에는 책임과 경계를, 주요 메서드에는 입력·반환값·예외와 제약을 Javadoc으로 설명한다.
- 시각·단위·집계 구간·null 의미처럼 호출자가 알아야 할 내용을 명시한다. 구현하지 않은 보장이나 검증하지 않은 성능을 주석에 약속하지 않는다.
- 필요한 `@param`, `@return`, `@throws`, `{@link ...}`를 사용한다. 단순 getter나 명백한 생성자 설명을 반복하지 않는다.
- 변경 시 주석도 함께 갱신하고 `./gradlew javadoc`으로 링크·문법을 확인한다.

## IntelliJ 프로젝트 설정

- `.idea/codeStyles/Project.xml`: 공식 IntelliJ XML의 설정을 프로젝트 스킴으로 저장한다.
- `.idea/codeStyles/codeStyleConfig.xml`: 프로젝트별 스타일 사용을 활성화한다.
- `.editorconfig`: Java 파일의 공백·개행·줄 길이 설정을 맞춘다.
- IntelliJ에서 서버 폴더를 프로젝트로 열면 프로젝트 스타일을 사용한다. Settings → Editor → Code Style → Java에서 Scheme은 Project다.
- 코드 정렬은 Reformat Code, import 정리는 Optimize Imports를 사용한다. 저장 시 자동 정렬은 이번 설정에서 켜지 않는다.
- IDE는 모든 규칙을 강제하지 않는다. 예를 들어 기존 wildcard import와 이름·책임은 별도로 확인한다. Gradle/CI 스타일 검사는 아직 구성하지 않았다.

## 참고 및 검증

- 공식 설정 원본: [intellij-java-wooteco-style.xml](https://github.com/woowacourse/woowacourse-docs/blob/e2f102b97f6b65f5ba8da09944ee8cb9b33b696a/styleguide/java/intellij-java-wooteco-style.xml).
- 원본 옵션을 보존하고 스킴 이름을 Project로 변경한 뒤 IntelliJ 프로젝트 컴포넌트로 감쌌다. Java 설정과 원본 옵션 일치를 확인한다.
- 2026-11-15: 전체 Java 소스·테스트 46개 파일에 공식 XML을 사용하는 IntelliJ CLI 포매터를 적용했다. wildcard import 제거와 import 정렬은 별도로 수행했다. 이는 Checkstyle 전체 규칙 검사를 뜻하지 않는다.
