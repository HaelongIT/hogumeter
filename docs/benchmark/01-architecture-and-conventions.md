# benchmark · 01. 아키텍처 · 컨벤션

## 패키지 / 계층
전역 구조는 `docs/01-architecture.md`(헥사고날)가 정본. 이 모듈이 차지하는 위치:

- **core `domain/`** (순수 Java, Spring·JPA·IO 0, `Clock` 주입, 랜덤 없음)
  - `domain/benchmark` — 기준가 계산(BenchmarkView 산출)·표본 3단 판정·자동확장
  - `domain/deal` — DealEvent 병합·교차검증·상태기계·이상치 판정
  - `domain/matching` — 토큰화·별칭 사전·3단 판정
- **core `application/`** — 파이프라인 유스케이스(신규 글 처리, 기준가 조회) + `port/`(in/out 인터페이스)
- **core `adapter/`** — persistence(JPA), web(REST), scheduler(폴링 트리거)
- **collector** — `parsers/`(사이트별 순수 함수, fixture 테스트) → `pipeline/`(정규화·적재) → `scheduler/`

## 요청 흐름
1. **수집**: collector 폴링 → 파싱 → `raw_deal_post` 적재(DB 계약, 멱등 upsert) — core와 직접 호출 없음.
2. **처리(core 스케줄러)**: 신규/변경 원문 → 가격 정규화 → 매칭(3단) → DealEvent 병합/생성 → 이상치 판정 → 상태 전이 → 알림 판정으로 전달(AL 모듈 경계).
3. **조회**: web/텔레그램 → REST → usecase → `domain/benchmark` 순수 계산(기간 P 내 DealEvent 목록 입력 → BenchmarkView 출력). 결과는 저장하지 않는다 — 항상 재계산.

## 재사용 / 편집 금지
- collector는 **krepe90/user-hotdeal-bot 골격 최대 재활용**(폴링·상태 변화 추적 기구현).
- **Flyway 마이그레이션은 core 단독 소유** — collector는 마이그레이션 금지, 계약 테이블(`raw_deal_post`)만 접근(docs/01 §DB 계약).
- 의사결정 로직(진짜/사기 판단 등)을 만들지 않는다 — 절대 원칙 2. 시스템은 reviewQueue에 올릴 뿐.

## 모듈 로컬 컨벤션
- 수치 파라미터는 도메인 파라미터 객체 한 곳에 기명 상수로 격리(seam): `MERGE_PRICE_TOLERANCE`(±α) / `MERGE_WINDOW_HOURS`(24~48h) / `OUTLIER_IQR_MULTIPLIER` / `COLDSTART_JACKPOT_RATIO`(30% 가안) / `K_DISPLAY`(사용자, 기본 5) / `K_FILL = max(7, K_DISPLAY+2)`(시스템) / `EXPAND_LIMIT_MONTHS`(12). 값 확정 전에는 테스트에서 명시 주입.
- 분포 입력 가격은 일관되게 `price_first`만 사용. `price_min`/`price_last`는 참고 표기 전용.
