# 21. TDD 가이드라인 & 교훈 메모리 프로토콜

## 사이클 규율
1. 요구 ID(예: BM-06) 선택 → 수용 기준을 테스트 목록으로 분해(테스트 이름 먼저 나열)
2. Red: 실패 테스트 1개 작성 (컴파일 실패도 Red)
3. Green: 통과하는 최소 구현
4. Refactor: 중복 제거·이름 정리. 테스트 그린 유지
5. 커밋: `[BM-06] red/green/refactor: ...` — 사이클 단위 커밋 권장
- 프로덕션 코드를 테스트 없이 작성했음을 발견하면: 멈추고 테스트를 소급 작성 후 lessons에 기록.

## 테스트 피라미드 (이 프로젝트의 형태)
- **단위(대다수)**: domain 패키지 — 기준가·이상치·매칭·알림·3계층 필터·생애주기 diff. IO 0, Clock 주입, 파라미터라이즈드 적극 사용.
- **파서 golden 테스트**: fixture HTML → DTO 스냅샷 비교. fixture는 스파이크에서 채취하고 `tests/fixtures/{site}/{케이스}.html`로 버전 관리. 사이트 구조 변경 시 fixture 갱신 + 변경 내용 field-notes 기록.
- **어댑터 슬라이스**: @DataJpaTest+Testcontainers(persistence), @WebMvcTest(web), WireMock(naver). 얇게.
- **통합(소수)**: compose 기반 스모크 — "글 fixture 주입 → 매칭 → 병합 → 알림 판정 → 발송 fake 검증" 종단 1~2본.
- **금지**: 실 사이트 네트워크 호출 테스트(스파이크 스크립트는 tests 밖 `spikes/`에 격리), 시간 sleep 기반 테스트, 순서 의존 테스트.

## 테스트 데이터 원칙
- 도메인 테스트는 빌더/픽스처 함수로 가독성 확보 (`aDealEvent().withPrice(890_000).crossVerified()`).
- 경계값을 요구 문서에서 직접 도출: K_display 3/5/10, n=0/1/4/5/7, 윈도우 경계, quiet hours 정각, ±α 경계.
- 확률·랜덤 없음. 모든 테스트는 결정적.

## 교훈 메모리 프로토콜 (CLAUDE.md 규정의 상세)
**언제 기록하나**: (a) Red가 예상과 다르게 실패/성공했을 때 (b) 버그 수정 시 (c) 리팩토링에서 설계 냄새를 발견했을 때 (d) 외부 세계(사이트·API)가 문서·가정과 다르게 동작했을 때 (e) 같은 실수를 2번째 했을 때(즉시 규칙 승격 대상).

**어디에**:
- `docs/99-lessons.md` — append-only 로그. 엔트리 형식:
  ```
  ## 2026-07-10 | BM-03 매칭
  증상: 용량 토큰 "1TB"가 "1테라"와 미매칭
  원인: 정규화 사전에 한글 단위 부재
  교훈(규칙): 숫자+단위 정규화는 한/영 양방향 사전 필수. 새 단위 발견 시 사전+테스트 동시 추가
  테스트: NormalizerTest#koreanCapacityUnits
  ```
- `docs/98-field-notes.md` — 사이트/API별 실측 사실(셀렉터, 응답 특성, 쿼터 실동작, 차단 징후, fixture 채취일).
- CLAUDE.md `## 축적된 규칙` — 반복 적용 가능한 규칙만 한 줄 승격. 프로젝트 보편 규칙이면 여기, 특정 사이트 한정이면 field-notes에 유지.

**세션 훅**: 세션 시작 시 두 파일을 읽는다. 세션 종료(또는 큰 작업 완료) 시 "이번에 기록할 교훈이 있는가"를 자문하고 없으면 없다고 넘어간다 — 억지 기록 금지, 누락 금지.

## 모듈별 최소 시나리오 카탈로그 (첫 테스트 목록의 씨앗)
- benchmark: SUFFICIENT/SPARSE/NONE 3단 출력 계약, 자동확장 발동·상한, 백필 혼합 n 계산, 이상치 제외 후 median, goodDealLine은 교차검증만
- matching: 별칭 히트, 미상 강등, CANDIDATE 경계, 확정 시 사전 축적
- deal: 병합 성립/불성립(±α·윈도우), 미상 잠정 병합→확정, 상태 전이 전수(NEW→ACTIVE→VERIFIED→ENDED + PRICE_CHANGED)
- alert: 트리거 매트릭스, 1발 원칙, quiet 보류·플러시·🔥관통, 후속 자격
- used: 3계층 조합, SORT/TRIGGER 분기, 목록 diff 4종(신규/변동/SOLD/끌올)

## 2차 기능 테스트 매트릭스 (종결 장치 표 = 테스트 원천)
> 출처: `working-area/2nd-plan-intake.md` B-13. 2차 기획의 **"종결 장치 표"가 곧 테스트 매트릭스**다 — 각 표의 셀이 파라미터라이즈드 케이스가 된다. 관련 순수 도메인 모듈: purchase/signal·cadence/digest(+ watch/priority 조건부, `docs/01`).
- **집합 술어**(`docs/03` 3-1): pricingSet/occurrenceSet/signalSet 각 자격 술어 × (CONFIRMED/CANDIDATE·키워드 히트·UPPER/LOWER 미확정/승격/기각·배치유보·⚠️라벨·신선도) — 동일 딜이 집합마다 다르게 분류되는지.
- **시간 좌표**(`docs/03` 3-2): firstSeen 불변(재분류·후행 postedAt 후에도), 유효 창 ∩ observedFrom, 관측시계 정지(수집 공백).
- **가격 역할 3분법**(priceFirst/Last/Min): 소비처별 올바른 역할 선택(percentile=priceFirst, 신호/비교=priceLast, 회고/최저기회=priceMin), priceMax 참조 금지.
- **PUR 상태×트리거 표**(`docs/15` PUR-03): 4트리거 × 4상태, paidPrice "<" 경계, 복수 관찰 OR, 성적표 발급 전제 게이트.
- **이상치 생애주기·배치**(`docs/11` BM-05, DN-C4): 유입 1회 판정 영속·드리프트 재평가 없음, 백필 PENDING_BATCH→완료 일괄 판정, PROVISIONAL 해소 4경로, 변경 서열(사람>배치>잠정).
- **알림 계열 표**(`docs/12` B-5): 발송 단위(상태 사건당 1통·병기), 계열 서열(트리거>후속), 라이브 재평가(플러시 시 갱신·제거 O/신규 X).
- **DIGEST 창·귀속**(`docs/18`): 반개구간 ∩ 관측 가동, 플로우 귀속=가시화 시각, 저장물 발송 성공 후 갱신(원자성).
- 확정본 충돌 대기 항목(DN-C*)은 승인 후 기대값 확정 — 그 전엔 잠정 표기.
