# benchmark · 06. TDD 가이드 · 테스트 데이터

> 전역 정본은 `docs/21-tdd-guidelines.md`. 여기는 이 모듈의 전략·픽스처만.

## 테스트 전략 (04의 AC와 매핑)
- **단위(대다수)**: `domain/benchmark`·`domain/deal`·`domain/matching` — BM-03~07 AC 전부 IO 0 순수 테스트. 파라미터라이즈드 적극(BM-06 AC-7: n 0/1/4/5/7 × K_DISPLAY 3/5/10).
- **파서 golden**: BM-01 AC-3 — `tests/fixtures/{site}/{케이스}.html` → DTO 스냅샷. fixture는 M0 스파이크에서 채취, 구조 변경 시 fixture 갱신 + field-notes 기록.
- **어댑터 슬라이스**: BM-01 AC-1(멱등 upsert)은 @DataJpaTest+Testcontainers. 얇게.
- **통합(소수)**: "글 fixture 주입 → 매칭 → 병합 → 기준가 → 알림 판정" 종단 1~2본(compose 스모크).
- **금지**: 실 사이트 호출, sleep 기반, 순서 의존.

## 테스트 데이터 / 픽스처
- 빌더 패턴: `aDealEvent().withPriceFirst(890_000).crossVerified().origin(BACKFILL)` — 가독성이 곧 요구 추적성.
- 경계값은 요구 문서에서 직접 도출: ±α 정확 경계, 윈도 경계, n=4/5, K_FILL 불변식, 12개월 상한.
- 수치 파라미터는 테스트에서 명시 주입(파라미터 객체) — `docs/31-detailed-params.md` 확정 전에도 테스트는 결정적.

## 부수효과 주입
- `Clock` — 윈도 판정·자동확장·firstSeen 전부 주입 시각 기준. `Instant.now()` 직접 호출 금지.
- persistence·알림 발송·네이버 조회 — port 인터페이스로 주입, 단위 테스트에서 목/페이크.
- 랜덤 없음 — 모든 테스트 결정적.
