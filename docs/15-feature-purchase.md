# 15. 기능(2차)1 — 구매 기록·관찰 모드·성적표 (PUR)

> 출처: `working-area/2nd-plan-intake.md` B-7. "호구였나"의 최종 판정 루프 + "산 뒤 알림" 구멍을 관찰 모드로 해소. **신품 한정**. 집합·시간·as-of 규약은 `docs/03`.
> 마일스톤: `decisions-needed.md` DN-M(권고 M5). 기반 참조: firstSeen(DN-C2), 이상치 생애주기(DN-C4).

## PUR-01 Purchase 개체 (독립 관찰 상태)
- variant 정책을 **점유하지 않음** — variant당 복수 Purchase 공존 가능(독립 관찰).
- 필드: `variantId`, `demandAxisValue`(SPLIT 필수·GROUPED 선택), `paidPrice`(as-paid), `purchasedAt`(사용자 입력, 날짜만이면 23:59 KST), `observationDays`(기본 90, 자동확장 없음), `linkedDealEventId?`(재구성 시 null + 이력).
- 상태: `OBSERVING → REPORT_PENDING → CLOSED`. (아카이브는 `ARCHIVED` — PUR-06)
- **삭제 = 구매 전 복원**(별도 경로, 아카이브 불발동). 상태별 삭제 매트릭스 3행(OBSERVING/REPORT_PENDING/CLOSED).

## PUR-02 스냅샷 (구매 시점 as-of)
- purchasedAt 시점 as-of + **기록 시점 설정** 동결(basis 동결).
- 컬럼: `[benchmarkPrice? / tier / n / m / sparseLowest? / paidGap? / basis]`.
- `UNOBSERVED`: purchasedAt < observedFrom(관측 시작 이전 구매)이면 스냅샷 통계 없음.
- 백필 중 기록 = **접수 즉시**, basis 동결은 **배치 완료로 유예**.
- 수정 규칙(필드별): purchasedAt→재계산 / paidPrice→paidGap만 / observationDays→종료 시점만.

## PUR-03 관찰 모드 — 상태 × 트리거 표 (정본)
구매 후에도 알림을 유지하되, 상태에 따라 트리거를 달리한다.

| 트리거 | OBSERVING | REPORT_PENDING | CLOSED | ARCHIVED |
|---|---|---|---|---|
| 🔥 대박딜(LOWER) | on | on | on | **off** |
| 목표가 | on | on | on | off |
| **paidPrice 하회** | **on** | off | off | off |
| 상대평가(다른 관찰 대비) | off | off | on(관찰 전·CLOSED만) | off |

- `paidPrice` 트리거 = OBSERVING만, **"<" 경계**(어느 활성 관찰의 paidPrice보다든 미만이면 발화 + 대상 표기), **서열 최하위**.
- 복수 관찰 = 트리거 열별 **OR**.
- 관할 각주: variant 트리거 한정(딜 층 핀 후속은 독립 — WATCH).

## PUR-04 성적표 (발급 = 관찰 종료 판정)
- **산입**: `firstSeen ∈ 관찰기간 AND capturedAt ≤ 발급`, 유효 창 ∩ observedFrom.
- **percentile** Y = X/n (동가 미포함), 집합 = pricingSet.
- **"기간 내 최저 기회"** = 관찰 **만료 시점 동결** min(priceMin, `docs/03` 3-3), 지각 백필분은 capture 시점 스칼라.
- **발급 전제**: 만료 AND 배치 완료 AND 미분류 유예 종결(대상 = variant 미처리 큐 전체, 키워드 제안 제외 / 48h·기산=전제 충족·1회) → **quiet 발송(관통 없음)**.
- 재발급 없음. 발급 후 유입분은 각주로만.
- **표기 규격**: [창 → 사전(percentile) → 사후("최저 기회") → 공시 → 조건부 각주 4종(basis 토글 / K 차이 / 범위 밖 구매 / 발급 후 유입)]. 성분별 잣대 분리: percentile=priceFirst, "최저 기회"=priceMin(라벨 교체), 병존 각주.

## PUR-05 관찰 문맥 (상세 화면, Purchase별 1줄)
- 딜 있음: "활성 딜(priceLast 최저) — 상회 구매 대비 −Y%".
- 딜 없음: "관찰 D일차 — 내 구매가보다 싼 기회 N번"(priceMin < paidPrice, pricingSet CONFIRMED 한정).
- REPORT_PENDING: "성적 집계 중".

## PUR-06 아카이브·재활성화·범위 밖 축값
- 아카이브 = CLOSED 전이 이벤트 + 다른 활성 관찰 없을 때(이벤트 구동, 삭제는 불발동).
- 재활성화: 수동(설정 복원) / 재구매(자동).
- 범위 밖 축값 구매 = 선택 추가 1클릭 제안 + 거부 시 각주·알림 범위 고지.
- 유머 등급 라벨 없음(정직성 — `docs/03` 상위 원칙, D 폐기안).

## 열린 것
- 마일스톤 배치(초안 M5 — DN-M 재확인).

## 테스트 포인트
- Purchase 상태기계: OBSERVING→REPORT_PENDING→CLOSED→ARCHIVED 전이, 복수 관찰 공존, 삭제 3행 매트릭스.
- 상태×트리거 표(PUR-03): 4트리거 × 4상태 순수 판정 매트릭스, paidPrice "<" 경계, 복수 관찰 OR, 서열 최하위 병기.
- 성적표 발급 전제(만료 ∧ 배치완료 ∧ 유예종결 48h) 게이트, percentile Y=X/n 동가 처리, UNOBSERVED, 각주 4종.
- 스냅샷 basis 동결 vs 백필 유예, 수정 규칙 필드별.
