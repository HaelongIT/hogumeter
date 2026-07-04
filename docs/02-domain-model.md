# 02. 도메인 모델 & DealEvent 상태기계

## 개체 (필드는 방향 제시 — 정확한 DDL은 M0 산출물)

### Product / Variant
- Product: name, category, demandAxisMode(GROUPED|SPLIT), 등록일
- Axis 정의: 제품별 priceAxis(예: 용량)와 demandAxis(예: 색상) 및 허용 값 목록 — 사용자가 등록 화면에서 정의
- Variant: priceAxis 값 조합 1개 = 1 Variant. 분포·기준가·알림의 기본 단위
- AliasDictionary: 매칭용 별칭(제품별 + 전역). reviewQueue 확정에서 자동 축적

### DealPost (원문) / DealEvent (병합 딜)
- DealPost: site, postId(자연키 UNIQUE), url, title, bodyText?, headlinePrice, postedAt, capturedAt, reactionScore?, raw(JSONB)
- DealEvent: variantId?, unclassified 여부, priceFirst/Min/Max/Last, shipping, basePrice?, appliedConditions[], confidence, origin(LIVE|BACKFILL), sourceSites[], crossVerified(m≥2), outlierFlag(NONE|UPPER|LOWER), status, firstSeen/lastSeen
- 병합 규칙: 동일 variant(또는 미상 시 후보군) + 가격 ±α + 시간 윈도우. 미상끼리 잠정 병합 허용, 사람 분류 시 확정 재평가(소급 알림 없음)
- 대표가 = priceFirst (BACKFILL은 as-shown 값, 교차검증 요건 면제)

### DealEvent 상태기계 (알림 발화 지점 명시)
```
        (수집)                (2번째 사이트)              (본문 가격 변화)
 NEW ──────────▶ ACTIVE ──────────────▶ VERIFIED ─────┐
                  │  ▲                        │        │ PRICE_CHANGED (상태 아님, 이벤트)
                  │  └────────────────────────┘        ▼
                  │            (품절/삭제/종료 감지)  ACTIVE/VERIFIED
                  └──────────────────────────────────▶ ENDED
```
- 알림 매핑: NEW→ACTIVE 진입 시 **첫 알림(즉시, 검증상태 표기)** / →VERIFIED 전이 시 **후속 "✅ N개 사이트 검증"** / PRICE_CHANGED 이벤트 시 **후속(인하·인상·재풀림)** / →ENDED 시 **후속(품절·종료)** — 알림이 나갔던 딜 한정
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

## 기준가 계산 계약 (benchmark 도메인의 공개 API)
입력: 기간 P 내 DealEvent 목록(variant 단위) + 정책(K_display 등) + 현재가
출력(BenchmarkView): tier(SUFFICIENT|SPARSE|NONE), benchmarkPrice?(n≥K_display일 때만), goodDealLine?(교차검증 딜 P25), periodLowest?(교차검증 딜 min), latestDeal, n, m, expandedToMonths?(자동확장 시), gap
- 이상치 제외 후 계산. SPARSE 구간은 통계 필드 null + 사례 리스트 반환 — 표시 계층이 아니라 **도메인이 정직성을 강제**한다.
