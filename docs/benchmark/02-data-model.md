# benchmark · 02. 데이터 모델

> **상태: M0-3 Flyway V1 적용됨.** 아래 테이블은 `core/src/main/resources/db/migration/V1__init.sql`이 정본이며 이 문서와 일치한다(변경 시 V2 마이그레이션 + 이 문서 동시 갱신). 개체 방향의 정본은 `docs/02-domain-model.md`. used(중고) 테이블은 V1 제외 — M2 V2 이월(`docs/91` Q-4).
> 컬럼 세부(제약·인덱스·체크)는 V1 SQL을 직접 볼 것 — 아래 표는 요약이다.

## 테이블 (제안 · 미적용)

| 테이블 | 주요 컬럼 | 제약/인덱스 | 설명 |
|---|---|---|---|
| `raw_deal_post` | site, post_id, url, title, body_text?, headline_price?, posted_at, captured_at, reaction_score?, status, raw(JSONB) | **UNIQUE(site, post_id)** — 멱등 upsert | collector↔core 유일 계약. raw JSONB는 크롤링 원본 보관 전용 |
| `deal_event` | variant_id?(미상=NULL), unclassified, price_first/min/max/last, shipping, base_price?, applied_conditions[], confidence, origin(LIVE\|BACKFILL), cross_verified, outlier_flag, status, first_seen, last_seen | INDEX(variant_id, first_seen) | 병합된 딜 1건. 대표가=price_first |
| `deal_event_source` | deal_event_id, site, raw_deal_post_id | UNIQUE(deal_event_id, raw_deal_post_id) | sourceSites[] — m(교차검증 수) 산출 근거, 원문 링크 1급 보존 |
| `alias_dictionary` | product_id?(NULL=전역), alias, token_pattern | UNIQUE(product_id, alias) | 매칭 별칭. reviewQueue 확정 시 자동 축적 |
| `review_queue_item` | type(UNCLASSIFIED\|OUTLIER_LOWER\|KEYWORD_SUGGEST), payload(JSONB), status(PENDING\|CONFIRMED\|REJECTED), channel(TELEGRAM\|WEB), created_at, resolved_at? | INDEX(status, created_at) | 사람 승격 큐 — 처리 UI는 기능3 소관, 행 생성은 BM |

- `product` / `variant` / `alert_policy`는 **registration·alerts 모듈 소유** — 여기서는 FK 참조만.
- **BenchmarkView는 테이블이 아니다** — 조회 시 순수 계산, 저장 금지(항상 재계산).

## 코드값
- DealEvent `status`: `NEW → ACTIVE → VERIFIED → ENDED` (+`PRICE_CHANGED` 이벤트 — 컬럼 아님, 가격 이력·알림 트리거)
- `origin`: `LIVE`(폴링 관측) / `BACKFILL`(과거 글 소급 — 교차검증 요건 면제, as-shown 가격)
- `outlier_flag`: `NONE` / `UPPER`(기준가 제외·무알림) / `LOWER`(기준가 제외·🔥최우선·승격 큐)
- 사기 기각된 LOWER 딜: **영구 제외** 표시(승격 큐 REJECTED와 연결) — 재수집돼도 표본 복귀 금지

## 라이프사이클
1. collector가 `raw_deal_post` upsert — 재실행해도 결과 불변(REL-01). 글 상태 변화(가격 변경·품절·삭제)는 status·raw 갱신으로 기록.
2. core가 신규/변경분을 읽어 매칭 → 기존 DealEvent에 흡수(병합)하거나 새로 생성. 흡수된 원문은 `deal_event_source`에 추가.
3. 상태 전이(docs/02 상태기계). ENDED 후에도 행 보존 — 기준가 표본은 과거 딜로 구성되므로 **삭제 없음**(하드/소프트 모두 없음).
4. 미상(unclassified) DealEvent는 사람 분류 시 variant 확정·병합 재평가(소급 알림 없음).

## DDL
정본 = `core/src/main/resources/db/migration/V1__init.sql` (롤백 `db/rollback/R1__init_rollback.sql` 동반, REL-05). `FlywayMigrationTest`가 Testcontainers로 11테이블 생성·`raw_deal_post` 자연키 UNIQUE를 검증한다. ERD는 이 SQL의 FK(`product`→`variant`→`alert_policy`/`price_history`, `deal_event`↔`deal_event_source`↔`raw_deal_post`) 그대로.
