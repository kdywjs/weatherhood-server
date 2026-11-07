# 날씨 전달 객체 설계

공통 계약의 원본은 `weatherhood-specs/contracts/weather-api.md`다. 이 문서는 Java/Spring 구현 내부의 책임과 협력만 설명한다.

## 조회 협력
1. Controller는 HTTP 입력을 받고 QueryService에 현재 요약 또는 날짜별 자료를 요청한다.
2. QueryService는 Grid에게 좌표 변환을 요청하고 SupportedLocations에게 현재 지원 범위를 확인한다.
3. Collector는 Repository에 이미 저장한 자료가 있는지 묻는다. 없으면 같은 자료의 작업을 공유하고 Gateway에게 수집을 요청한다.
4. Gateway는 RequestBudget에 호출을 예약하고 HTTP 통신한다. PayloadParser는 결과 코드·페이지·격자·발표·대상 시각을 검증해 WeatherBatch를 만든다. Gateway는 전체 페이지가 맞을 때만 수집 결과를 반환한다.
5. Repository가 수집본을 저장한다. 원본 종류·발표와 대상 시각을 섞어 덮어쓰지 않는다.
6. QueryService는 저장본을 WeatherTimeline에 전달한다. 도메인이 값별 실황·예보 우선순위, 나중에 발표한 예보의 과거 적용 금지, 결측과 출처를 판단한다.
7. 요약은 WeatherOverview가 현재 실황·다음 24시간·날짜별 공급 최고/최저를 구성한다. Controller/DTO는 이 결과를 직렬화 형태로만 바꾼다.

## 판단의 소유자
- Grid는 기상청 공식 투영을, WeatherSource는 발표 시각과 공급 간격을 안다.
- WeatherTimeline은 어떤 값을 사용할지와 사용할 수 없는 구간을 안다. HTTP·SQL·Spring을 알지 못한다.
- WeatherValues는 기온·강수량 등 사실을 가진다. 강수량의 경계·기간·정성 의미를 별도 값 객체로 보존한다.
- WeatherOverview는 하루 최고/최저·대표 날씨를 임의 계산하지 않는다. 미정인 집계는 결측 이유로 남긴다.
- Collector는 언제·무엇을 가져올지만 조율한다. 수치를 보정하거나 날씨 상태를 판단하지 않는다.
- Repository는 저장 책임을, Gateway는 외부 통신 책임을 갖는다. 서로를 직접 호출하지 않는다.
- DTO에는 수집·우선순위·날짜 집계 규칙을 두지 않는다.

## 실행과 확장 제약
단일 서버와 로컬 H2로 검증한다. 인프라 교체는 Repository/Gateway 경계를 유지하며, 운영 DB·다중 서버 잠금·운영 재시도 정책은 확정 후 반영한다. 재사용할 근거가 없는 범용 베이스 클래스나 모든 Service의 인터페이스는 두지 않는다.

현재 저장본은 Jackson으로 직렬화한 내부 WeatherBatch이며 DB SQL은 H2 전용이다. 다른 운영 DB로 전환할 때 데이터 이전·스키마 호환성·트랜잭션 검증을 함께 해야 한다.
