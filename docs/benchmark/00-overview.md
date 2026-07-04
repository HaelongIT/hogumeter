# benchmark · 00. 개요

## 목적 / 범위
기능2(BM) — **수집 → 가격 정규화 → 매칭 → 딜 병합·교차검증 → 이상치 → 기준가 산출**의 전 파이프라인. 시스템의 심장이자 테스트 자산의 핵심. 사용자 = 운영자 1인.

- 이 문서 묶음의 경계: `docs/11-feature-benchmark-engine.md`(BM-01~07)의 상세화. 정책 충돌 시 `docs/90-planning-final-v1.2.md` §2가 최종 권위.
- 걸치는 컴포넌트: collector(BM-01·02 수집·추출)와 core(BM-02~07 정규화·매칭·병합·산출). 알림 발화 자체(AL)와 제품 등록(REG)은 별도 모듈 — 여기서는 이음새만 정의.
- 수치 파라미터(±α, 병합 윈도, IQR 배수 등)는 **기명 상수로만 참조**(`docs/91-open-questions.md` Q-1). 값 확정 = M0 `docs/31-detailed-params.md`.

## 기능 목록
| 기능ID | 이름 | 설명 |
|---|---|---|
| BM-01 | 핫딜 수집 | 뽐뿌·루리웹·펨코 폴링(게시판당 1req/min), 글 상태 변화 추적, (site, postId) 멱등 |
| BM-02 | 가격 정규화 | 실결제가+배송비, as-posted(역산 금지), headline_price 필수, 추출 실패는 스킵 로그 |
| BM-03 | 매칭 (v1 탈모델) | 토큰화+별칭 사전, CONFIRMED/CANDIDATE/REJECTED 3단, 재현율 우선, 미상 버킷 |
| BM-04 | 딜 병합·교차검증 | 동일 variant+가격 ±α+시간 윈도, crossVerified(m≥2), 미상 잠정 병합, BACKFILL 면제 |
| BM-05 | 이상치 (양방향) | median/IQR 컷. UPPER=제외·무알림 / LOWER=제외+🔥최우선+승격 큐. 제외 키워드 |
| BM-06 | 기준가 산출 | BenchmarkView 계약 — 3단 tier, median/P25/최저가, K_fill 자동확장, 갭 |
| BM-07 | 사후학습 키워드 제안 | 오알림 "무시" → 빈출 토큰 → 제외 키워드 후보 → 승격 큐(KEYWORD_SUGGEST) |

## 상태 / 권한 / 정책
- **DealEvent 상태기계**: `NEW → ACTIVE → VERIFIED → ENDED` + `PRICE_CHANGED`(상태 아님, 이벤트). 전이·알림 매핑은 `docs/02-domain-model.md`가 정본.
- **코드값**: origin `LIVE|BACKFILL` / outlierFlag `NONE|UPPER|LOWER` / 매칭 판정 `CONFIRMED|CANDIDATE|REJECTED` / 표본 tier `SUFFICIENT|SPARSE|NONE` / ReviewQueue type `UNCLASSIFIED|OUTLIER_LOWER|KEYWORD_SUGGEST`, 상태 `PENDING|CONFIRMED|REJECTED`.
- **권한**: 1인용 — 인가 계층 없음(사설망 전제). 공개망 노출 시 최소 인증은 이월 항목(docs/90 §10).
- **핵심 정책(요약)**: 단일 사이트 딜은 median 포함, P25·최저가 타이틀은 교차검증 딜만 / 대표가 = price_first / 시간 가중 없음 / n(전체 유효 딜)이 판정 단위, m(교차)은 표시 전용 — "n건(교차 m건)" 이중 표기.

## 엔드포인트 매핑(요약)
| 기능 | 엔드포인트 |
|---|---|
| BM-06 기준가 조회 | `GET /api/v1/variants/{variantId}/benchmark` (상세: 03) |
| BM-01~05·07 | 엔드포인트 없음 — 수집·배치 파이프라인. 승격 큐 처리 API는 알림 모듈(기능3) 소관 |
