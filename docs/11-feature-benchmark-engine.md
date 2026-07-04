# 11. 기능2 — 수집·매칭·기준가 엔진 (BM)

## BM-01 핫딜 수집 (collector)
- 대상: 뽐뿌·루리웹 핫딜·펨코 핫딜. 게시판당 1req/min, 지수 백오프, robots 존중.
- 글 상태 추적: 제목/본문 가격 변경, 품절 표시, 삭제 감지 (오픈소스 골격 재활용).
- reactionScore(추천·댓글 수) 가능한 만큼 수집. 사이트 간 정규화는 M0 수치 확정 항목.
- 멱등: (site, postId) UNIQUE.

## BM-02 가격 정규화
- 저장 기준 = 실결제가 + 배송비(무료=0, 무조건 합산). headline_price 필수, base_price/appliedConditions[]/confidence는 추출 가능 시만. **카드·쿠폰 역산 시도 금지** — as-posted 원칙.
- 제목/본문에서 가격·배송비 추출은 규칙 기반(정규식+휴리스틱). 추출 실패 글은 미상 아님 — 가격없음으로 스킵 로그.

## BM-03 매칭 (v1 탈모델)
- 정규화 문자열 매칭: 토큰화 + 별칭 사전 + 숫자/용량 패턴. **임베딩·LLM 금지(v1).**
- 3단 판정: CONFIRMED(자동 배정) / CANDIDATE(reviewQueue행) / REJECTED. 재현율 우선 — 애매하면 CANDIDATE.
- 축값(라인업·용량·색상) 판별 불가 시 미상 버킷. 사람 확정 시 별칭 사전에 자동 축적.

## BM-04 딜 병합·교차검증
- 병합: 동일 variant + 가격 ±α + 윈도우. 미상은 후보군 기준 잠정 병합. 소급 알림 없음.
- crossVerified = 소스 사이트 ≥ 2. BACKFILL은 면제.

## BM-05 이상치 (양방향)
- 판정: median/IQR 기반(수치 M0). UPPER → 기준가 제외·무알림. LOWER → 기준가 제외 + 🔥 최우선 알림 + reviewQueue(진짜→표본 포함 / 사기→영구 제외).
- SPARSE/NONE 구간 폴백 컷: 현재가 기준 비상식 구간은 사례 나열·최저가 잣대에서 잠정 제외 + reviewQueue.
- 제외 키워드 매칭 글은 정책에 따라 배제 또는 ⚠️라벨.

## BM-06 기준가 산출
- 02 문서의 BenchmarkView 계약 그대로. n 판정 / m 표시 / K_fill=max(7,K_display+2) 자동확장(상한 12개월, 확장 사실 표기) / 시간 가중 없음 / benchmarkPrice는 n 전체 median, goodDealLine·periodLowest는 교차검증 딜만.

## BM-07 사후학습 키워드 제안
- 오알림 "무시" 시 해당 글 빈출 토큰에서 제외 키워드 후보 추출 → reviewQueue(KEYWORD_SUGGEST) → 수락 시 정책 반영.

## 테스트 포인트 (이 기능이 테스트 자산의 핵심)
- benchmark 도메인: 표본 0/1/4/5/7건, 백필 혼합, 이상치 혼입, K_display 경계(3·5·10), 자동확장 상한 — 파라미터라이즈드 테스트 필수.
- 이상치: 약정 0원(LOWER인데 키워드로 배제되는 케이스), 진짜 대박딜, UPPER 끼워팔기.
- 병합: 3사이트 동시/시차 유입, 미상 잠정 병합 후 확정, 가격 ±α 경계.
- parsers: 사이트별 golden HTML — 정상/가격변경/품절/삭제 fixture.
