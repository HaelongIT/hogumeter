# 02. 도메인 모델 & DealEvent 상태기계

## 개체 (필드는 방향 제시 — 정확한 DDL은 M0 산출물)

### Product / Variant
- Product: name, category, demandAxisMode(GROUPED|SPLIT), 등록일
- Axis 정의: 제품별 priceAxis(예: 용량)와 demandAxis(예: 색상) 및 허용 값 목록 — 사용자가 등록 화면에서 정의
  - **수요축 값 선택(v1.3, DN-C6)**: 기본 = 전체 값 선택. 미선택 값의 딜은 "범위 외"로 전 집합 제외(REJECTED 아님 — 재선택 시 편입). 부분 선택은 미상 큐 증가 비용을 등록 화면에서 고지. → `docs/10` REG-02.
- Variant: priceAxis 값 조합 1개 = 1 Variant. 분포·기준가·알림의 기본 단위
  - `observedFrom`(v1.3): variant별 관측 시작 시점 = min(백필 성공 사이트 도달) 또는 등록 시각. 모든 시간 통계의 유효 창 하한. → `docs/03` 3-2
- AliasDictionary: 매칭용 별칭(제품별 + 전역). reviewQueue 확정에서 자동 축적

### DealPost (원문) / DealEvent (병합 딜)
- DealPost: site, postId(자연키 UNIQUE), url, title, bodyText?, headlinePrice, postedAt, capturedAt, reactionScore?, raw(JSONB)
- DealEvent: variantId?, unclassified 여부, priceFirst/Min/Max/Last, shipping, basePrice?, appliedConditions[], confidence, origin(LIVE|BACKFILL), sourceSites[], crossVerified(m≥2), outlierFlag(NONE|UPPER|LOWER), status, firstSeen, lastEvidenceAt, capturedAt
- 병합 규칙: 동일 variant(또는 미상 시 후보군) + 가격 ±α + 시간 윈도우. 미상끼리 잠정 병합 허용, 사람 분류 시 확정 재평가(소급 알림 없음)
- 대표가 = priceFirst (BACKFILL은 as-shown 값, 교차검증 요건 면제)
- **시각 좌표(v1.3, `docs/03` 3-2)**: `firstSeen` = **발생 시각**(라이브도 postedAt 우선, 파싱 실패 시 첫 관측 폴백 / 백필=postedAt) — 생성 시 1회 확정·불변 **[조건부 DN-C2: 라이브 postedAt 우선은 M0 실측 후 확정, `docs/91` Q-23]**. `capturedAt` = 수집 시각(항상 별도 보존). `lastEvidenceAt` = max(최신 병합 firstSeen, 마지막 PRICE_CHANGED) — 살아있음의 적극 증거.
- **가격 역할 3분법(v1.3)**: priceFirst=발생·분포(기준가·percentile) / priceLast="지금"(신호·비교·다이제스트 활성) / priceMin="지나간 기회"(회고·성적표 최저기회) / priceMax=역할 없음·참조 금지.
- **outlierFlag 생애주기(v1.3, DN-C4)**: 유입 시 1회 평가 후 **영속**(분포 드리프트 재평가 없음). 전 시스템 단일 판정 원천. 변경 서열 사람 > 배치 > 잠정(사람 플래그 불가침). 백필 딜은 배치 완료 시 완전 분포로 일괄 판정(PENDING_BATCH). → `docs/11` BM-05.

### DealEvent 상태기계 (알림 발화 지점 명시)
```
        (수집)                (2번째 사이트)              (본문 가격 변화)
 NEW ──────────▶ ACTIVE ──────────────▶ VERIFIED ─────┐
                  │  ▲                        │        │ PRICE_CHANGED (상태 아님, 이벤트)
                  │  └────────────────────────┘        ▼
                  │            (품절/삭제/종료 감지)  ACTIVE/VERIFIED
                  │  ▲(재개: 동일 dealEventId 가격 재관측)
                  └──┴───────────────────────────────▶ ENDED
```
- **재개 전이(v1.3, DN-C1)**: `ENDED`는 **잠정 종료**로 취급 — 동일 `dealEventId`가 가격과 함께 재관측되면 `ACTIVE`로 복귀(전이 이력 로그). 재개는 **새 첫 알림이 아니라 후속 "부활"**. WATCH 부활·재핀(`docs/17`)·SIG 신호(`docs/16`)가 이 전이에 의존.
- 알림 매핑: NEW→ACTIVE 진입 시 **첫 알림(즉시, 검증상태 표기)** / →VERIFIED 전이 시 **후속 "✅ N개 사이트 검증"** / PRICE_CHANGED 이벤트 시 **후속(인하·인상·재풀림)** / →ENDED 시 **후속(품절·종료)** / **ENDED→ACTIVE 재개 시 후속(부활)** — 알림이 나갔던 딜 한정
- 병합으로 흡수된 DealPost는 새 알림을 만들지 않는다(첫 알림 중복 금지). 흡수가 VERIFIED 전이를 일으키면 그 후속 알림만.
- 댓글 기반 변화는 추적하지 않는다(확정 경계).

### PriceHistory / Alert / ReviewQueue
- PriceHistory: variantId, source(NAVER), price, fetchedAt — 온디맨드 + 1h 캐시
- AlertPolicy: variantId, targetPrice?(선택), periodP, kDisplay, excludeKeywords[](전역+제품별), quietHours?, demandAxis 필터
- ReviewQueueItem: type(UNCLASSIFIED|OUTLIER_LOWER|KEYWORD_SUGGEST), payload, 상태(PENDING|CONFIRMED|REJECTED), 처리 채널(TELEGRAM|WEB)

### 중고: UsedSearch / Listing / 메모·축
- UsedSearch(Product 종속): platform(BUNJANG), requiredKeywords[], bonusGroups[{keywords[], mode(SORT|TRIGGER)}], excludeKeywords[], targetPrice, pollIntervalMin(기본10)
- Listing: platform, listingId(자연키), title, price 이력, status(ACTIVE→SOLD/REMOVED), promoted(알림 승격 여부), detailFetched(승격 시 1회)
- 당근 매물: 평가기 입력(URL fetch 시도→텍스트 복붙→수동)으로 Listing(platform=DAANGN, manual) 생성 가능
- 메모·축 (EAV 3테이블): listing_note(자유 메모) / comparison_axis(제품별 축 정의) / listing_axis_value(승격 값). JSONB로 뭉치지 말 것

### 2차 개체 (Purchase / WatchItem) — v1.3
- **Purchase**(`docs/15`): variantId, demandAxisValue(SPLIT 필수), paidPrice, purchasedAt, observationDays(기본 90), linkedDealEventId?, 상태(OBSERVING→REPORT_PENDING→CLOSED, 아카이브 ARCHIVED). variant 정책 비점유(복수 공존). 신품 한정.
- **WatchItem**(`docs/17`, **[WATCH-유보]** — 개념 채택·배치 M6): dealEventId + anchorPostId(재구성 시 자동 승계) + note. 딜당 활성 핀 1개. 결말 BOUGHT/MISSED/DROPPED. 대상 = DealEvent만.
- (SIG/CAD/DIGEST는 신규 수집 0 read-model — 개체 추가 없음, `docs/16`·`docs/18` 순수 함수)

## 기준가 계산 계약 (benchmark 도메인의 공개 API)
입력: 기간 P 내 DealEvent 목록(variant 단위) + 정책(K_display 등) + 현재가
출력(BenchmarkView): tier(SUFFICIENT|SPARSE|NONE), benchmarkPrice?(n≥K_display일 때만), goodDealLine?(교차검증 딜 P25), periodLowest?(교차검증 딜 min), latestDeal, n, m, expandedToMonths?(자동확장 시), gap
- 이상치 제외 후 계산. SPARSE 구간은 통계 필드 null + 사례 리스트 반환 — 표시 계층이 아니라 **도메인이 정직성을 강제**한다.
