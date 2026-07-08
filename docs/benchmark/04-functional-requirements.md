# benchmark · 04. 기능 요구 — 인수조건 (TDD 기준)

> **이 문서가 M1 TDD의 기준이다.** 각 인수조건(AC)을 테스트로 1:1 변환한 뒤 구현한다(Red → Green → Refactor). 정책 정본: `docs/90-planning-final.md` §2. 수치 파라미터는 기명 상수(`docs/benchmark/01` §모듈 로컬 컨벤션, 값은 M0 `docs/31-detailed-params.md`) — 테스트에서는 명시 주입한다.

---

## BM-01. 핫딜 수집 (collector)

### AC-1. 동일 글 재수집은 멱등이다
- **Given** `(site=ppomppu, postId=123)` 원문이 이미 저장돼 있다
- **When** 같은 글을 다시 수집한다
- **Then** 새 행이 생기지 않고(UNIQUE(site, post_id) upsert), 내용이 같으면 결과 불변이다(REL-01)

### AC-2. 글 상태 변화를 감지한다
- **Given** 저장된 글의 fixture와 "품절 표시가 추가된" 같은 글의 fixture
- **When** 변경본을 재수집한다
- **Then** 상태 변화(품절)가 기록된다 (가격 변경·삭제도 동일 패턴)

### AC-3. 사이트별 파서 golden 테스트
- **Given** `tests/fixtures/{site}/{케이스}.html` — 뽐뿌·루리웹·펨코 × 정상/가격변경/품절/삭제
- **When** 파싱한다
- **Then** 제목·headline가·URL·postedAt·reactionScore가 스냅샷 DTO와 일치한다 (실 네트워크 호출 금지)

<!-- 테스트 케이스:
  - [ ] AC-1 재수집 멱등(내용 동일/변경 각각)
  - [ ] AC-2 상태 변화 3종(가격변경·품절·삭제)
  - [ ] AC-3 golden 3사이트 × 4케이스 -->

---

## BM-02. 가격 정규화

### AC-1. 저장 기준 = 실결제가 + 배송비
- **Given** "3만원 + 배송비 3,000원" 글과 "3.3만원 무료배송" 글
- **When** 정규화한다
- **Then** 두 글 모두 headline_price = 33,000 (무료배송은 배송비 0으로 합산 기준 통일)

### AC-2. as-posted — 카드·쿠폰 역산 금지
- **Given** "N카드 할인 시 890,000" 형태의 글
- **When** 정규화한다
- **Then** 890,000이 그대로 분포 입력이 되고, applied_conditions에 태그(카드명)만 남는다. base_price 역산 시도는 없다

### AC-3. 가격 추출 실패는 "가격없음 스킵"이지 미상이 아니다
- **Given** 가격 패턴이 없는 글
- **When** 정규화한다
- **Then** DealEvent가 생성되지 않고 스킵 로그가 남는다 (미상 버킷과 구분 — 미상은 "제품 축 판별 불가", 스킵은 "가격 자체 부재")

<!-- 테스트 케이스:
  - [ ] AC-1 배송비 합산/무료배송 0원
  - [ ] AC-2 조건부 가격 태그 보존, 역산 없음
  - [ ] AC-3 가격 부재 스킵 + 로그
  - [ ] 경계: "원" 없는 숫자, 만원 단위 축약("89만") 파싱 -->

---

## BM-03. 매칭 (v1 탈모델 — 임베딩·LLM 금지)

### AC-1. 별칭 사전 히트 → CONFIRMED 자동 배정
- **Given** 별칭 사전에 (아이폰17 → product-1), variant 축값(용량=256GB) 정의
- **When** "아이폰 17 256기가 자급제 89만" 제목을 매칭한다
- **Then** CONFIRMED, variant(256GB)에 자동 배정된다

### AC-2. 애매하면 CANDIDATE — 재현율 우선
- **Given** 제품 토큰 일부만 일치하는 제목
- **When** 매칭한다
- **Then** REJECTED가 아니라 CANDIDATE로 판정되고 reviewQueue(UNCLASSIFIED) 행이 생성된다 (놓침 > 오알림)

### AC-3. 축값 판별 불가 → 미상 버킷
- **Given** 제품은 확실하나 용량 토큰이 없는 제목
- **When** 매칭한다
- **Then** unclassified DealEvent(variant NULL, 후보군 유지)로 들어간다

### AC-4. 사람 확정 시 별칭 자동 축적
- **Given** CANDIDATE 항목을 사람이 특정 variant로 확정한다
- **When** 확정 처리된다
- **Then** 해당 표현이 별칭 사전에 추가되어, 같은 제목이 다음엔 CONFIRMED로 매칭된다

<!-- 테스트 케이스:
  - [ ] AC-1 별칭 히트·축값 배정
  - [ ] AC-2 CANDIDATE 경계(부분 일치 스코어 경계값)
  - [ ] AC-3 미상 강등
  - [ ] AC-4 확정 → 사전 축적 → 재매칭 CONFIRMED
  - [ ] 숫자/용량 패턴: "1TB"↔"1테라", "256G"↔"256GB" -->

---

## BM-04. 딜 병합·교차검증

### AC-1. 병합 성립 → 교차검증 → VERIFIED 전이
- **Given** 동일 variant, 가격 차 ≤ MERGE_PRICE_TOLERANCE, 시간 차 ≤ MERGE_WINDOW_HOURS인 서로 다른 두 사이트 글
- **When** 두 번째 글이 처리된다
- **Then** 단일 DealEvent로 병합되고 sourceSites=2, crossVerified=true, 상태가 ACTIVE→VERIFIED로 전이된다

### AC-2. 병합 불성립 — 경계 밖은 별개 딜
- **Given** 가격 차 > MERGE_PRICE_TOLERANCE (또는 시간 차 > 윈도)인 두 글
- **When** 처리한다
- **Then** 별개 DealEvent 2건이 된다 (±α·윈도 정확 경계값 포함/제외 테스트 필수)

### AC-3. 미상끼리 잠정 병합 — 확정 시 재평가, 소급 알림 없음
- **Given** 미상 버킷의 두 글: 제품 후보군 겹침 + 가격 ±α + 48h 이내
- **When** 처리한다
- **Then** 잠정 병합·잠정 검증 카운트. 사람이 variant 확정하면 병합이 재평가되되, 이미 지나간 알림 기회는 소급 발화하지 않는다

### AC-4. 흡수된 글은 새 첫 알림을 만들지 않는다
- **Given** 알림이 이미 나간 DealEvent
- **When** 세 번째 사이트 글이 흡수된다
- **Then** 첫 알림은 생성되지 않는다. 이 흡수가 VERIFIED 전이를 일으키는 경우 그 후속 알림 판정만 AL 모듈로 전달된다

### AC-5. BACKFILL은 교차검증 요건 면제
- **Given** origin=BACKFILL 단독 사이트 딜
- **When** 기준가 표본을 구성한다
- **Then** crossVerified=false여도 n(유효 표본)에 포함된다 (as-shown 가격, 백필 표기)

### AC-6. 상태기계 전이 전수
- **Given/When/Then** `NEW→ACTIVE`(수집) / `ACTIVE→VERIFIED`(2번째 사이트) / `ACTIVE|VERIFIED→ENDED`(품절·삭제·종료) / PRICE_CHANGED 이벤트(본문 가격 변화 — 상태 불변) — 허용 전이 전수 + 비허용 전이 거부

<!-- 테스트 케이스:
  - [ ] AC-1 2사이트 병합·VERIFIED
  - [ ] AC-2 가격 경계(±α 정확 경계), 윈도 경계
  - [ ] AC-3 미상 잠정 병합 → 확정 재평가 → 소급 알림 없음
  - [ ] AC-4 흡수 글 첫 알림 금지
  - [ ] AC-5 백필 면제
  - [ ] AC-6 상태 전이 전수(3사이트 동시/시차 유입 포함) -->

---

## BM-05. 이상치 (양방향 분리)

### AC-1. UPPER — 조용히 제외
- **Given** 표본 분포와 median/IQR 컷(OUTLIER_IQR_MULTIPLIER)을 위로 벗어난 딜(약정 실질가·끼워팔기)
- **When** 이상치 판정한다
- **Then** outlierFlag=UPPER, 기준가 계산 제외, 알림 없음

### AC-2. LOWER — 제외하되 🔥 최우선
- **Given** 아래로 벗어난 딜(가격오류 대박딜 또는 사기)
- **When** 판정한다
- **Then** outlierFlag=LOWER, 기준가 계산 제외, 🔥 최우선 알림 대상 표시, reviewQueue(OUTLIER_LOWER) 행 생성

### AC-3. 승격/기각 — 판단은 사람
- **Given** OUTLIER_LOWER 큐 항목
- **When** "진짜였다"로 확정하면 → 표본에 포함되어 기준가 재계산에 반영된다
- **When** "사기·낚시"로 기각하면 → 영구 제외된다(재수집돼도 복귀 없음)

### AC-4. 제외 키워드 — 배제 vs ⚠️라벨 토글
- **Given** "약정" 포함 글, 전역 제외 키워드셋
- **When** 정책이 "제외"(기본)면 → 알림·기준가에서 배제된다
- **When** 정책이 "⚠️라벨만"이면 → 표본에 포함하되 의심 라벨이 붙는다

### AC-5. SPARSE/NONE 구간 폴백 컷
- **Given** median이 없는 SPARSE 구간 + 현재가 대비 비상식 가격의 글
- **When** 처리한다
- **Then** 사례 나열·최저가 잣대에서 잠정 제외되고 reviewQueue로 올라간다 (오염 방어)

<!-- 테스트 케이스:
  - [ ] AC-1 UPPER 제외·무알림 (끼워팔기 케이스)
  - [ ] AC-2 LOWER 제외·🔥·큐행 (진짜 대박딜 케이스)
  - [ ] AC-3 승격 → 재계산 포함 / 기각 → 영구 제외
  - [ ] AC-4 키워드 배제/라벨 토글 (약정 0원: LOWER인데 키워드로 배제되는 교차 케이스)
  - [ ] AC-5 SPARSE 폴백 컷 -->

---

## BM-06. 기준가 산출 (BenchmarkView — 시스템의 심장)

### AC-1. SUFFICIENT — 정식 산출
- **Given** 기간 P 내 유효 딜 n ≥ K_DISPLAY (교차검증 + 단일 사이트 + 백필, 이상치 제외), 현재가
- **When** 계산한다
- **Then** tier=SUFFICIENT / benchmarkPrice = **n 전체의 median**(시간 가중 없음, 균등) / goodDealLine = **교차검증 딜만의 P25** / periodLowest = **교차검증 딜만의 min+날짜** / "n건(교차 m건)" 이중 표기 / 분포 입력은 일관되게 price_first

### AC-2. 단일 사이트 딜의 가중 분리
- **Given** n=6 중 교차검증 m=2, 단일 사이트 4
- **When** 계산한다
- **Then** median은 6건 전체로, P25·최저가는 2건(교차)으로만 산출된다

### AC-3. SPARSE — 통계 용어 금지
- **Given** n이 1~4건
- **When** 계산한다
- **Then** tier=SPARSE, benchmarkPrice·goodDealLine = **null**(도메인이 강제), 사례 리스트(가격·날짜·출처)만 반환. 알림 잣대는 보유 사례 중 최저가

### AC-4. NONE — 관측 없음
- **Given** n=0
- **When** 계산한다
- **Then** tier=NONE, 현재가만. 대박딜 폴백: 현재가 대비 COLDSTART_JACKPOT_RATIO 이상 싼 딜 유입 시에만 "기준 미확립·참고용" 알림 대상

### AC-5. K_fill 자동확장 — 상한 12개월
- **Given** 기간 P 내 n < K_FILL(=max(7, K_DISPLAY+2)), P 밖 과거에 추가 딜 존재
- **When** 계산한다
- **Then** n ≥ K_FILL이 될 때까지(최대 EXPAND_LIMIT_MONTHS=12) 기간을 확장하고 expandedToMonths에 확장 사실을 표기한다. 상한까지 확장해도 미달이면 확보된 표본으로 tier를 재판정한다

### AC-6. 갭 계산
- **Given** benchmarkPrice=890,000, periodLowest=820,000, 현재가=990,000
- **When** 계산한다
- **Then** gap = 현재가−기준가(+100,000 / +11.2%), 현재가−최저 핫딜가(+170,000 / +20.7%) — 원·% 병기

### AC-7. 경계값 계약 (파라미터라이즈드 필수)
- n = 0 / 1 / 4 / 5 / 7 (K_DISPLAY=5 기준 tier 경계), K_DISPLAY = 3 / 5 / 10 각각에서 K_FILL 불변식(K_FILL > K_DISPLAY) 보존, 백필 혼합 n 계산, 이상치 혼입 시 제외 후 median

<!-- 테스트 케이스:
  - [ ] AC-1 SUFFICIENT 정식 산출(median·P25·min·이중 표기)
  - [ ] AC-2 단일 사이트 포함/타이틀 분리
  - [ ] AC-3 SPARSE null 강제 + 사례 나열
  - [ ] AC-4 NONE + 대박딜 폴백 경계(30% 가안)
  - [ ] AC-5 자동확장 발동·표기·12개월 상한
  - [ ] AC-6 갭 원·% 병기
  - [ ] AC-7 파라미터라이즈드: n 0/1/4/5/7 × K 3/5/10 -->

---

## BM-07. 사후학습 키워드 제안

### AC-1. 오알림 "무시" → 제외 키워드 후보 제안
- **Given** 알림이 나간 딜에 사용자가 "무시"를 누른다
- **When** 사후학습이 동작한다
- **Then** 해당 글 빈출 토큰에서 제외 키워드 후보를 추출해 reviewQueue(KEYWORD_SUGGEST) 행을 만든다 (자동 반영 금지 — 판단은 사람)

### AC-2. 수락 시 정책 반영
- **Given** KEYWORD_SUGGEST 항목을 수락한다
- **When** 반영된다
- **Then** 제외 키워드 목록(전역 또는 제품별)에 추가되어 이후 매칭·알림·기준가에서 BM-05 AC-4 규칙을 따른다

<!-- 테스트 케이스:
  - [ ] AC-1 무시 → 토큰 추출 → 큐행(자동 반영 없음)
  - [ ] AC-2 수락 → 키워드셋 반영 → 후속 글 배제 확인 -->
