# 91. 기술 보류 보드 (Open Questions)

> **잠정값으로 진행 가능한 기술 보류·한계·미결**의 단일 보드. 각 항목은 **잠정값 + 재개 트리거**(무엇이 충족되면 다시 처리)를 반드시 갖는다.
> 사람이 정해야 할 기획·정책 결정은 여기가 아니라 `working-area/decisions-needed.md`. 해소되면 `working-area/decision-log.md`로 옮기고 여기서 제거.
> 매 세션 시작 시 읽는다.

## 항목 양식

```
## [열림] Q-N. <제목>
- 맥락: 어디서 왜 생겼나 (관련 요구 ID)
- 잠정값: 지금 무엇으로 진행 중인가 (seam 위치 — 상수/인터페이스/설정)
- 재개 트리거: 무엇이 충족되면 처리하나
```

---

_(Q-1. 기준가 수치 파라미터 — **해소됨 2026-07-04**: docs/31 6값 운영자 승인 → `BenchmarkParams.defaults()` 상수화. decision-log 참조. reactionScore 정규화는 알림 가중 전용이라 M1 지연 무방(docs/31 하단), 여기서 제거.)_

_(Q-2. 전역 API 컨벤션 — **해소됨 2026-07-08**: 배선 슬라이스 1·2에서 확정. **봉투 없는 리소스 직접 반환**(RegistrationController·BenchmarkController) + 에러 `{code, message}`(`ApiExceptionHandler` @RestControllerAdvice: BM_VARIANT_NOT_FOUND→404, BM_INVALID_PERIOD→400). decision-log 참조. 여기서 제거.)_

## [열림] Q-3. 네이버 쇼핑 API 스파이크·연동 보류 (키 미발급)
- **맥락**: M0-4 스파이크 중 "아이폰 17 256" 응답 품질 확인은 네이버 개발자센터 앱 등록(Client ID/Secret)이 필요한데 미발급 상태(사용자 확인). 현재가(BM-06 currentPrice)·기능1 등록 후보 조회의 데이터 소스.
- **잠정값**: 네이버 어댑터는 port 인터페이스만 두고 미구현. 기준가 계산·테스트는 현재가를 주입값으로 대체(도메인은 이미 현재가를 입력으로 받음).
- **재개 트리거**: 키 발급 → 루트 `.env`에 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET` 주입 → 스파이크 실행(응답 품질·정확 매칭 가능성) → 결과 `98-field-notes` → 어댑터 구현.

## [열림] Q-4. used(중고) 스키마는 V2로 이월
- **맥락**: M0-3 Flyway V1은 신품 코어 루프(M1=REG+BM+AL)만 담았다. 중고(기능5)의 UsedSearch/Listing/EAV 메모·축 테이블(`docs/02-domain-model.md`)은 M2 범위라 V1에서 제외.
- **잠정값**: V1에 미포함. used 도메인 코드·테이블은 M2 착수 시 `V2__used.sql`로 추가.
- **재개 트리거**: M2(중고) 착수 → used 모듈 문서 세트(`docs/used/`) 작성 → V2 마이그레이션 TDD.

_(Q-5. 뽐뿌 golden fixture 재채취 — **해소됨 2026-07-09**: 재채취 **불필요**. "baseList 계열 셀렉터 전무"라는 실측 기록이 오류였다(실제로 없는 건 `#revolution_main_table` **요소** 하나뿐). 같은 파일에서 딜 행 21건이 정상 파싱된다. UA 위장 금지(원칙5)라 운영에서도 이 응답을 받으므로 **이 fixture가 정본**이다. `list_ua_mismatch.html` → `list_normal.html` 개명, `parsers/ppomppu.py` GREEN. `docs/98` 2026-07-09 항목·decision-log 참조. 여기서 제거.)_

## [열림] Q-6. 분위수 방식 = R-7 선형보간 (BM-06 median·P25)
- **맥락**: `docs/benchmark/04` BM-06은 median·P25(goodDealLine)를 요구하나 분위수 산출 방식은 미명시. 방식에 따라 AC-1/AC-7 기대값이 달라진다.
- **잠정값**: **R-7(선형보간, Excel PERCENTILE.INC/R 기본)** 채택 — `Quantiles`(순수 헬퍼) 1곳에 격리, BM-05 Tukey Q1/Q3도 재사용. 대안: nearest-rank(goodDealLine을 실관측가로) — 정직성 측면 이점.
- **재개 트리거**: 1차 검증(아이폰 17 256GB)에서 goodDealLine 표시가 운영자 직관과 어긋나면 `Quantiles`에서 방식 교체(테스트 기대값 동반 갱신).

## [열림] Q-7. SUFFICIENT인데 교차검증 m=0
- **맥락**: n ≥ K_DISPLAY(SUFFICIENT)이나 교차검증 딜이 0건이면 goodDealLine(P25)·periodLowest의 표본(교차만)이 빈다. AC 미커버.
- **잠정값**: `goodDealLine`·`periodLowest` = null, `benchmarkPrice`는 전체 n으로 정상 산출(`BenchmarkCalculator`의 `crossVerified.isEmpty()` 분기). 정직성 우선.
- **재개 트리거**: 실데이터에서 단일사이트만으로 SUFFICIENT가 잦아 goodDealLine 부재가 문제되면 정책 재검토(decisions-needed 승격).

## [열림] Q-8. SPARSE 잣대는 cases[] 최저가로 도출
- **맥락**: `docs/benchmark/03` line 17은 periodLowest=교차 min, AC-3(`04` line 178)은 SPARSE 알림 잣대=보유 사례 중 최저가. SPARSE에서 어느 필드가 무엇을 담는지 충돌.
- **잠정값**: SPARSE에서 통계필드(benchmarkPrice·goodDealLine·periodLowest) 전부 null 유지, 알림 잣대는 소비자(AL 모듈)가 `cases[]`에서 min 도출. 04(TDD 정본)가 03(제안·미적용)에 우선.
- **재개 트리거**: AL 모듈(기능3) 착수 시 SPARSE 알림 판정 구현 — cases 기반 최저가 잣대 확정, 필요 시 03 문서 정합.

## [열림] Q-9. 자동확장 월 연산·expandedToMonths 의미
- **맥락**: 자동확장(AC-5)의 월 경계 연산과 `expandedToMonths` 표기 의미가 미명시. `Instant`엔 월 개념이 없다.
- **잠정값**: 월 연산은 Clock의 `ZoneId` 기준 `ZonedDateTime.minusMonths`. `expandedToMonths` = **실제로 딜이 추가된 최원거리 개월**(데이터 확보 span). 과거 딜이 없어 표본이 안 늘면 미발동(null) — 경계 테스트가 이를 no-op으로 격리. 12개월 상한 밖 딜은 절대 미포함.
- **재개 트리거**: 표시 계층에서 "최근 N개월 기준" 문구가 필요하거나 월 경계(캘린더 vs 30일)가 실데이터와 어긋나면 재정의.

## [열림] Q-10. 콜드스타트 잭팟은 BenchmarkView 필드 아님 (독립 술어)
- **맥락**: `docs/benchmark/03` 응답 계약에 잭팟 필드가 없고, AC-4는 잭팟을 "알림 대상"으로 규정. 뷰에 넣을지 별도로 뺄지.
- **잠정값**: `BenchmarkCalculator.qualifiesAsColdStartJackpot(dealPriceFirst, currentPrice, params)` 순수 술어로 분리 — 뷰는 tier=NONE만 표기. seam = 이 메서드 1곳.
- **재개 트리거**: AL 모듈(기능3)이 NONE 구간 알림을 배선할 때 이 술어를 호출 — 시그니처·소비 지점 확정.

## [열림] Q-11. includeOutliers 토글은 계산 진실 밖
- **맥락**: `docs/benchmark/03` line 9의 `includeOutliers`(기본 false)는 표시 손잡이 — "계산 진실은 불변". 순수 계산기는 항상 이상치를 제외한다.
- **잠정값**: `compute()`의 진실 경로엔 미진입(이상치 항상 제외). 토글은 표시용 별도 목록(향후 web 어댑터 관심사)로 이번 증분 범위 밖.
- **재개 트리거**: M1 web 슬라이스에서 기준가 화면 구현 시 — 이상치 목록 표시 경로를 계산 진실과 분리해 배선.

## [열림] Q-12. 병합 가격 허용폭의 기준가(base) = anchor 딜
- **맥락**: `MERGE_PRICE_TOLERANCE` = max(base×ratio, floor)에서 base를 두 딜 중 무엇으로 삼을지 docs 미명시(BM-04).
- **잠정값**: base = **기존(anchor·먼저 처리된) 딜의 priceFirst**. seam = `DealMergePolicy.priceWithinTolerance` 1곳. 경계 포함(≤). 시간 윈도도 경계 포함(48h 정확=병합).
- **재개 트리거**: 1차 검증에서 과분할(같은 딜 갈라짐)/오병합 관측 시 base 정의(min/max/평균) 재조정.

## [열림] Q-13. BM-04 병합의 알림 억제·소급 방지는 AL 모듈 관심사
- **맥락**: AC-3 "확정 시 소급 알림 없음", AC-4 "흡수 글은 새 첫 알림 금지"는 **알림 발화 규칙**. BM-04(순수 병합)는 병합 판정·상태 전이까지만 책임진다.
- **잠정값**: BM-04는 `canMerge`/`merge` + 상태 전이(흡수 시 NEW 리셋 없음, ACTIVE→VERIFIED)만 보장. 실제 첫 알림 억제·소급 방지는 AL 모듈이 상태기계 이벤트를 구독해 처리(docs/02 알림 매핑).
- **재개 트리거**: AL(기능3) 착수 시 상태 전이 이벤트 → 알림 판정 배선.

## [열림] Q-14. SPARSE 폴백 컷 밴드폭(absurdityRatio) — 미승인 잠정 파라미터
- **맥락**: BM-05 AC-5 SPARSE 구간 폴백 컷은 "현재가 대비 비상식 가격"을 걸러야 하나, 그 밴드 폭이 docs/31 승인 6개 파라미터에 없다(신규 정책 수치).
- **잠정값**: `absurdityRatio` = 0.5(±50%). `OutlierDetector.isAbsurdVsCurrent`에 **주입**(테스트/앱 레이어). BenchmarkParams(승인 seam)엔 아직 미편입 — 승인값과 미승인값을 섞지 않기 위함.
- **재개 트리거**: 운영자 승인 요청(docs/31에 7번째 행) → 승인 시 `BenchmarkParams`로 이관 + `defaults()` 편입 + decision-log. 1차 검증에서 오염 사례 관측해 폭 재조정.

## [열림] Q-15. 리뷰 큐 — 읽기는 생겼다. **승격·기각(쓰기)이 없다**
- **맥락**: AC-2 "🔥 최우선 알림", AC-3 승격/기각 UI는 알림·큐 처리 영역. BM-05(순수)는 outlierFlag 판정 + `ReviewQueueItem` 값 생성 + DealEvent 전이(promote/reject)까지만.
- **읽기 해소(2026-07-10)**: `GetReviewQueueUseCase` + `ReviewQueueController`(`GET /api/v1/review-queue`) + web `미상 큐` 탭 — **전부 신규 파일**, core 기존 파일 무수정. 그전까지 `review_queue_item`은 **쓰이기만 하고 아무도 읽지 않았다**(`IngestDealsUseCase`가 넣고 `PipelineScheduler`가 세기만 함). 매칭이 무엇을 놓치는지 볼 방법이 없었다 — 놓침을 허용하는 시스템(원칙 3)에서 놓친 것을 못 보면 그건 유실이다. `status`·`created_at`을 엔티티가 매핑하지 않아 JPA 대신 **읽기 전용 SQL**로 읽는다.
- **잠정값(쓰기 없음)**: `DealEvent.promoteFromOutlier()`·`reject()`는 순수 도메인에만 있고 **프로덕션 호출자가 없다.** 화면에 승격·기각 버튼을 그리지 않는다 — 못 하는 일을 버튼으로 그리면 눌러 보고 나서야 안다(과대약속 금지). 🔥 우선순위 발화는 AL 모듈(Q-20)이 OUTLIER_LOWER로 처리.
- **재개 트리거**(무엇이 참이 되어야 하는가): ① `DealEventEntity`가 승격·기각 전이를 표현할 수 있어야 한다 ② `ReviewQueueItemEntity`가 `status`(PENDING/CONFIRMED/REJECTED)를 쓸 수 있어야 한다 ③ **Q-27 ④가 해소되어 같은 근거가 한 행이어야 한다** — 지금은 매 틱 중복이 쌓여, 하나를 처리해도 나머지 N-1개가 남는다. ①②는 core 기존 파일이라 조율. (구현 수단은 여럿이다 — 위 셋은 조건이지 방법이 아니다.)

## [열림] Q-16. BM-03 CANDIDATE 임계 = 코어 토큰 1개 이상 겹침(재현율 우선)
- **맥락**: AC-2 "부분 일치 → CANDIDATE(REJECTED 아님)"의 부분 일치 스코어 경계값이 미명시.
- **잠정값**: **코어 토큰 교집합 ≥ 1이면 CANDIDATE**, 0이면 REJECTED. 별칭 substring 히트만 CONFIRMED. 매우 관대(놓침>오알림, 절대원칙 3) — 오알림은 사람이 큐에서 1초 컷. seam = `Matcher.match` 부분일치 분기 1곳.
- **재개 트리거**: 1차 검증에서 CANDIDATE 큐가 과다하면 임계 상향(토큰 비율·가중치 도입) — 여전히 탈모델 유지.

## [열림] Q-17. BM-03 AC-4 별칭 학습 = 정규화·공백제거 제목 전체를 축적(crude)
- **맥락**: "확정 시 표현이 별칭 사전에 추가돼 같은 제목이 다음엔 CONFIRMED"의 "표현" 추출 방식 미명시.
- **잠정값**: `Matcher.confirm`이 `TitleNormalizer.joined(title)`(공백제거 정규화 전체)을 별칭으로 학습. "같은 제목" 재매칭엔 충분하나 일반화(다른 표기 변형)는 못함. seam = `AliasDictionary.learn`.
- **재개 트리거**: 유사·변형 제목까지 CONFIRMED가 필요해지면 핵심 구절 추출(빈출 n-gram 등)로 학습 정교화.

## [열림] Q-18. BM-02 가격 파싱 휴리스틱 — 오검출 3종 수정 완료, 잔여 한계 4종
- **맥락**: AC 경계 "원 없는 숫자"를 지원하려 4자리 이상 숫자를 가격 후보로 보고 **첫 매치**를 취했다. 연도·모델번호·규격이 오검출될 수 있다는 우려가 있었다.
- **2026-07-09 실측 확인**: 라이브를 기다릴 필요가 없었다. fixture의 **실 제목 49건**(뽐뿌 21 + 루리웹 28)만으로 오검출이 재현됐다 — `1,000mg*60정 (9,600원/무료)` → **1000**, `5600MHz 513,000` → **5600**, `콘드로이친 1200 60정…(34,710원/무료)` → **1200**. 이런 값이 표본에 들어가면 Tukey 하한을 뚫어 **LOWER 이상치 → 🔥 최우선 오알림**이 된다.
- **수정(GREEN)**: 정규식 땜질이 아니라 **후보 서열**을 도입 — ① `만원` 축약 ② `원` 붙은 숫자(3자리 이상) ③ 콤마 구분 숫자(뒤 단위 제외) ④ 맨 4자리+ 숫자(뒤 단위 제외). 추가로 핫딜 제목 관례 **`(가격원/배송비)`** 인식(배송비 합산, BM-02 AC-1 준수), 3자리 원(`900원`) 검출, `카할`·`유배` 조건 태그 보존. 전수 대조: **69건 중 7건만 변경, 62건 불변**. 미검출 12 → 10건.
- **잔여 한계(열린 이유)**:
  1. **루리웹 목록은 긴 제목을 `…`로 자른다** → 가격이 잘려 미검출(2건: `MSI …(163,…`, `쓰리메가 … / 578,…`). 목록만으로는 복구 불가 — 글 본문 fetch가 필요(실 네트워크).
  2. **무료 딜은 여전히 `None`(스킵)**. `(무료/무료)`·`GOG 무료` 등 3건. 0원을 median 표본에 넣으면 기준가가 무너지므로 보수적으로 유지. **0원 편입 여부는 정책 결정** — 필요해지면 `decisions-needed`로 승격.
  3. **1~2자리 적립성 금액**(`라방 5원`, `클릭 44원`)은 의도적으로 미검출. 상품가가 아니다.
  4. ~~**펨코 구조화 경로의 배송 조건 소실**~~ → **해소(2026-07-09)**: `와우무배`·`네멤무료`·`1만5천원무료`를 `조건부무료배송:{원문}` 태그로 보존. 배송비는 as-posted 0 유지(역산 금지). 단 fixture에 유료배송(금액) 사례가 없어 그 경로는 여전히 미검증.
- **재개 트리거**: (1)(4)는 실 수집·글 본문 fetch 착수 시. (2)는 정책 결정 시. 새 오검출 패턴이 관측되면 `_UNIT` 목록과 서열을 조정(seam = `price.py` 1곳).

## [열림] Q-19. BM-01 파서 golden은 정상 리스트(list_normal)만 — 상태변화 케이스 fixture 미확보
- **맥락**: AC-3은 사이트×(정상/가격변경/품절/삭제) 매트릭스를 요구하나, M0 스파이크는 `list_normal`만 채취. 상태변화 fixture 없음. 뽐뿌는 fixture 자체가 부적합(Q-5).
- **잠정값**: 루리웹·펨코·번개 `list_normal` golden으로 파서 구현·검증 완료(정상 케이스). 품절 감지 로직은 존재(루리웹 제목 키워드, 펨코 `.hotdeal_var8Y`, 번개 status)하나 실 fixture 미검증. 상태변화 자체는 core JPA 슬라이스(`RawDealPostUpsertTest`)에서 검증.
- **재개 트리거**: 실 수집 착수 시 품절/가격변경/삭제된 실제 글을 fixture로 채취 → 파서 상태 감지 golden 추가.
- **진행(2026-07-09)**: 뽐뿌 파서 추가로 **4사 전부 `list_normal` golden 확보**(Q-5 해소 — 재채취 불필요). 다만 뽐뿌 `.end2` 품절 표식이 이 스냅샷엔 0건이라 SOLD_OUT 경로는 여전히 미검증. 상태변화 fixture 부재는 그대로 열려 있다.

## [열림] Q-20. AL 발송·플러시·봇·현재가는 어댑터/정지(토큰·키 필요)
- **맥락**: AL 순수 도메인(트리거·1발·게이트·후속 자격)은 완성. 실 발송(텔레그램)·봇 명령·현재가(네이버)는 외부 연동이라 무중단 정지 조건.
- **잠정값**: out-port `AlertSender`만 정의(fake로 검증). 텔레그램 어댑터·메시지 문자열 포맷(강도 아이콘·갭·링크)·봇 명령(/status·/queue·/mute)·인라인 버튼 콜백·현재가 조회(Q-3)·**보류분 플러시 오케스트레이션**(HOLD 큐 저장 + 종료 시 재디스패치 + 상태 재확인)은 미구현. 도메인 게이트는 플러시 시 재사용 가능.
- **재개 트리거**: 텔레그램 봇 토큰 + 네이버 키 발급 → `.env` 주입 → 어댑터 구현(실전송은 사용자 승인). pre-deploy-checklist 대상.

## [열림] Q-21. AL 트리거 세부 — "역대최저 근접" 여백·SPARSE/NONE 강도 매핑
- **맥락**: AL-02의 특가 조건 "P25 이하 **또는 역대최저 근접**"에서 "근접" 여백폭 미명시. SPARSE/NONE 폴백 알림의 강도 등급도 미명시.
- **잠정값**: 특가(SPECIAL) = SUFFICIENT & price ≤ P25(goodDealLine)로만 판정(P25 ≥ 최저이므로 역대최저는 포함, "근접 여백"은 별도 미모델). SPARSE(보유 최저가 이하)·NONE(콜드스타트 30%)은 강도 **GOOD**로 매핑하되 딱지("표본 N건 참고용"/"기준 미확립 참고용")로 신뢰도 구분. seam = `AlertEvaluator`.
- **재개 트리거**: 1차 검증에서 특가 알림이 너무 좁/넓거나 SPARSE/NONE 폴백 강도 조정이 필요하면 재조정.

## [열림] Q-22. BM-07 빈출 임계·"무시" 트리거 배선
- **맥락**: BM-07 사후학습 도메인(빈출 토큰 → KEYWORD_SUGGEST, 수락 시 제외셋 갱신)은 완성. 빈출 임계와 실제 트리거(텔레그램 "무시" 버튼)는 세부 미확정.
- **잠정값**: `KeywordSuggester` 빈출 임계 = **여러 무시 건 중 2건 이상 공통 토큰**(MIN_FREQUENCY=2), 토큰=공백 분리·한글/영문 포함·길이≥2. "무시" 버튼→post-learning 호출은 AL 텔레그램 어댑터(Q-20) 배선. 불용어/제품 토큰 정교화는 후순위.
- **재개 트리거**: 실 사용에서 후보 노이즈(제품명·일반어 오제안)가 많으면 불용어 사전·제품 토큰 제외·임계 상향.

---

_(이하 2026-07-08 2차 기획 통합에서 등장한 위임 항목. 출처: `working-area/2nd-plan-intake.md` B-2·B-6·B-13. 확정본 충돌은 여기가 아니라 `decisions-needed.md` DN-C1~6.)_

## [열림] Q-23. `postedAt` 파싱 가능성 실측 (라이브 포함)
- **맥락**: 시간 좌표계(B-2)·`firstSeen` 재정의(DN-C2)·CAD 주기(B-9)·성적표 산입(B-7)이 전부 딜 **발생 시각**에 의존. 백필은 목록에 날짜가 있으나, 라이브 글의 정확한 postedAt 파싱 가능성이 미실측.
- **잠정값**: firstSeen = **postedAt 우선, 파싱 실패 시 capturedAt(첫 관측) 폴백 / 백필=postedAt(날짜만이면 23:59 KST)**. capturedAt은 항상 별도 보존. seam = 수집 파이프라인의 firstSeen 산출 1곳.
- **재개 트리거**: M0 스파이크(뽐뿌·루리웹·펨코 목록/글에서 postedAt 파싱 성공률 실측, 라이브 포함) → `docs/98-field-notes` 기록 → DN-C2 확정(라이브 postedAt 우선 채택 여부).
- **진행(2026-07-09, 뽐뿌 실측)**: 뽐뿌 목록의 `.baseList-time`은 **당일 글 `21:10:11`(시:분:초) / 이전 글 `26/07/03`(YY/MM/DD)** 로 형식이 갈린다.
- **✅ 구현 완료(2026-07-09)**: 3사 전부 목록에 시각이 있고 **하나의 규칙으로 수렴**한다 — "시:분(:초)이면 당일, 날짜면 그 날짜". `pipeline/timestamps.py`(순수, `now` 주입, KST 고정 오프셋). 파서 포트가 `parse(body, now)`로 바뀌었고 `run_cycle`이 폴링 시각을 넘긴다.
  - 실측 형식: 뽐뿌 `21:10:11` / `26/07/03` · 루리웹 `날짜 18:10` / `날짜 2026.07.03` · 펨코 `20:59`
  - **날짜만이면 23:59 KST**(잠정값 그대로 — 시각을 지어내지 않는다는 표시)
  - **먼 미래(>12h)만 어제로 롤백**. 몇 분 앞선 값은 우리 시계가 뒤처진 것이라 그대로 둔다 — 하루를 되돌리면 24시간 오차가 된다.
  - 파급: core `IngestDealsUseCase:135`가 `firstSeen = postedAt ?? capturedAt`이므로, 이제 3사 딜의 `firstSeen`이 **수집 시각이 아니라 발생 시각**이 된다. 기간 P 필터·CAD 딜 주기·성적표 산입·백필이 전부 이 값에 의존했다.
- **잔여**: (a) 글 본문의 정확한 postedAt은 여전히 미확인(목록만 씀). (b) **"당일" 판정은 게시판이 자정에 형식을 바꾼다는 가정**에 의존한다 — 실 수집에서 자정 경계 샘플로 확인 필요. (c) DN-C2의 "라이브 postedAt 우선 채택 여부"는 이미 코드가 채택한 상태(C-2 확정본과 정합).

## [열림] Q-24. 신호 신선도 상한 = 잠정 48h
- **맥락**: SIG 신호등(B-8)의 신선도 3단 중 "확신" 상한 — 이 시간 내 lastEvidenceAt이면 신호 색을 확신으로 표시.
- **잠정값**: **48h**(가안). seam = 신선도 판정 상수(신호 도메인 파라미터). `docs/31` 위임행 등재.
- **재개 트리거**: 1차 검증 이후 실 딜 지속시간 분포 관측 → 확신/약화 경계 조정.

## [열림] Q-25. 신호 자격 한도 = 잠정 7일
- **맥락**: SIG 신선도 3단 중 "자격 상실"(신호 제외·정보 강등) 경계. 이 시간 지나면 "최근 딜 N일 전(종료 미확인)"으로 강등.
- **잠정값**: **7일**(가안). seam = 신호 도메인 파라미터. `docs/31` 위임행 등재.
- **재개 트리거**: Q-24와 동반 관측 — 신호가 너무 오래/짧게 살아있으면 조정.

## [열림] Q-26. 분석 기간 P 기본값 = 잠정 6개월
- **맥락**: P가 기준가(BM-06)에 더해 CAD 딜 주기(B-9)에서도 공용("분석 기간"으로 의미 확장). 기본값이 두 소비처에 공통 적용.
- **잠정값**: **6개월**(가안). seam = 정책 기본값 1곳(기준가 P와 공용). `docs/31` 위임행 등재. 손잡이 독자 목록(B-6)에 소비처 등재.
- **재개 트리거**: 1차 검증에서 기준가/주기 표본이 과소·과대면 조정. 카테고리별 P는 Phase 2.

## [열림] Q-27. 수집 파이프라인 "미처리" 마커 = deal_event_source 유무(재처리 여지)
- **맥락**: `IngestDealsUseCase.findUnprocessed`는 "deal_event_source 링크 없는 raw_deal_post"를 미처리로 본다. deal_event가 생기는 CONFIRMED 글엔 맞으나, **CANDIDATE(리뷰큐)·REJECTED·가격없음 스킵 글은 소스가 안 생겨** 다음 실행에 재처리·리뷰 항목 중복 여지.
- **잠정값**: 슬라이스 3은 `ingestPending()` 1회 처리 기준(테스트도 1회). 스케줄러 반복 실행 시 애매/스킵 글 재처리는 미해결. 이상치 판정(BM-05)·알림(AL) 연결은 **슬라이스 4로 재배치**(소비처와 응집).
- **재개 트리거**: 스케줄러 배선(반복 폴링) 착수 시 — raw_deal_post에 처리 마커(예: processed_at, V2 컬럼) 추가 또는 처리 이력 테이블로 멱등 보장.
- **진행(2026-07-09)**: 폴링 루프(`scheduler.loop.run_cycle`)는 생겼으나 **DB 배선이 없어 트리거는 아직 미발동**. 반복 폴링이 실제로 `raw_deal_post`를 쓰기 시작하는 시점(Q-36)에 이 항목을 처리한다 — 그때까지 애매/스킵 글 재처리 문제는 그대로 열려 있다.
- **⚠️ 트리거 발동(2026-07-09, Q-36 해소)**: collector가 이제 실제로 `raw_deal_post`에 **업서트**한다. 그래서 문제가 하나 커졌다 — 품절·가격변경이 `raw_deal_post`엔 반영되지만, 그 글에 이미 `deal_event_source` 링크가 있으면 **`findUnprocessed()`가 걸러내 core가 재처리하지 않는다.** 즉 **상태변화가 `deal_event`까지 도달하지 못한다**(BM-01 AC-2의 절반이 끊긴다). 애매/스킵 글 재처리 여지와 별개로 이게 더 시급하다. 처리 마커(`processed_at`) 대신 **`captured_at` 갱신을 감지하는 재처리 조건**이 필요할 수 있다(예: `deal_event.last_seen < raw.captured_at`). core 소유 영역이라 상대와 조율 대상.
- **✅ 상태→ENDED 절반 해소(2026-07-09)**: 신규 `ReprocessDealStatusUseCase.reprocessEndedDeals()` — 링크된 **모든** 원문이 SOLD_OUT/DELETED면 `deal_event`를 ENDED로, last_seen을 종료 근거 시각으로 단조 갱신. `findUnprocessed`·`IngestDealsUseCase` **무수정**(additive: `DealEventEntity.applyStatusChange`·`DealEventRepository.findByStatusIn`만). Testcontainers 4케이스(단일 품절·DELETED·다중소스 중 하나 ACTIVE 유지·전부 ACTIVE 무변). **잔여(여전히 열림)**: ~~① 가격변화 재처리~~ **해소(2026-07-10)**: 신규 `ReprocessDealPricesUseCase` + 순수 `PriceRefresh`. `priceFirst`·`firstSeen`·`status` 불변, `priceLast`="지금"(**활성** 원문 중 최신 관측 — 방금 품절된 최저가는 "지금 가격"이 아니다), `priceMin`="지나간 기회"(품절 원문도 포함), `lastSeen` 단조, 변화 없으면 미기록. 기존 core 파일 무수정(`DealEventMapper.toDomain`으로 crossVerified 복원). 스모크 5-1c가 `999000/899000/899000` 종단 증명. / ② `captured_at>last_seen` 변경 감지기(전수 스캔이라 정확성은 무관, 효율 seam), ③ **최초 수집 시 이미 품절인 원문 → 품절된 딜에 알림이 나간다**(2026-07-10 실측). `IngestDealsUseCase:137`이 원문 상태와 무관하게 `DealStatus.ACTIVE`로 딜을 만들고 `:110`에서 곧바로 `alertEvaluation.evaluate`를 태운다. 파이프라인 순서(ingest → 가격 → 종료) 덕에 **DB는 같은 틱에 자가치유**되지만(스모크 5-3이 증명) **알림은 이미 나간 뒤다.** 실측 로그: `[STUB alert] intensity=GOOD price=700000` → 직후 `deal_event.status = ENDED`. 지금은 `StubAlertSender`라 로그뿐이지만 **봇 토큰(Q-20)이 켜지는 순간 실전송된다.** 고치려면 `ingestOne`이 `post.getStatus()`를 보고 초기 상태를 정하거나(또는 종료될 딜의 알림을 억제), ④ **애매/스킵 글 재처리 중복 — 실측됨(2026-07-10)**: `findUnprocessed()`는 `deal_event_source` 링크가 없는 원문을 미처리로 본다. 매칭 실패 원문(UNKNOWN·CANDIDATE)은 딜을 만들지 않아 링크도 생기지 않으므로 **매 틱 다시 `review_queue_item`에 쌓인다.** 스모크 5-1e가 2초 주기에서 재현한다 — 운영 60초 주기면 원문 하나당 **하루 1,440행**. 리뷰 큐 조회(Q-15)는 같은 근거를 접어 `occurrences`로 세어 보여준다(**숨기지 않는다** — 조용히 지우면 결함이 사라진 것처럼 보인다). 그래도 승격·기각은 이게 고쳐져야 가능하다: 하나를 처리해도 나머지 N-1개가 남는다. 고치려면 처리 마커(`processed_at`)나 큐 업서트가 필요한데 둘 다 core 기존 파일/스키마다. — **②③④는 core 기존 파일 수정이 필요해 상대와 조율. ③은 Q-20 착수 전에 반드시.** / ~~⑤ 배치 오케스트레이션~~ **해소(2026-07-10)**: `adapter/scheduler/PipelineScheduler`가 `ingestPending()` → `reprocessPriceChanges()` → `reprocessEndedDeals()` 순으로 주기 호출(`core.pipeline.interval-ms`, 기본 60s). 종료가 마지막이라 닫히기 직전의 마지막 가격까지 반영된다. `@EnableScheduling`은 신규 `SchedulingConfig`에. 단계별 예외 격리 + `initialDelay=interval`로 `@SpringBootTest` 오염 방지. 매 틱 `PipelineTickReport` 카운터(OBS-02, Q-57).
- **③의 이음새 실측(2026-07-10)** — "core 기존 파일이라 조율"이 **이번엔 참이다.** Q-46·Q-48·Q-50처럼 신규 파일로 우회되는지 검증했고, 안 된다:
  - 알림은 `IngestDealsUseCase.ingestOne` 안에서 **동기로** 발화한다(`alertEvaluation.evaluate(...)`가 `sources.save(...)` 바로 다음 줄). 파이프라인이 같은 틱에 `ENDED`로 자가치유해도 알림은 이미 나갔다.
  - 고칠 수 있는 지점은 셋뿐이고 **전부 상대의 기존 파일**이다: `IngestDealsUseCase.candidateFrom`(초기 상태를 `post.getStatus()`에서 정한다 — **가장 정직한 한 줄**) · `EvaluateAlertOnDealUseCase.evaluate`(발화 전 원문 상태 확인) · `AlertDispatcher`(발송 직전 억제).
  - **신규 파일만으로 되는 유일한 길**은 `AlertSender` 포트에 데코레이터를 끼우는 것이다(`AlertMessage.deal`이 `site`·`sourceUrl`을 지니므로 발송 시점에 `raw_deal_post.status`를 조회해 억제할 수 있다). 그러나 `@Primary` 자기참조나 `BeanPostProcessor`가 필요하고, **상대의 알림 경로가 그들의 파일 변경 없이 조용히 달라진다.** 그건 소유권 규칙의 정신을 우회하는 것이라 하지 않았다.
  - **권고(상대에게)**: `candidateFrom`의 `DealStatus.ACTIVE`를 `post.getStatus()` 기반으로 바꾸는 한 줄. 그러면 종료 딜은 애초에 `ENDED`로 태어나고 `AlertEvaluator`가 자연히 걸러낸다. 스모크 5-3이 이미 자가치유를 관찰하고 있으니, 거기에 "알림이 나가지 않았다"는 단언만 더하면 회귀가 잠긴다.

## [열림] Q-28. C-5(⚠️라벨=전 통계 제외) 표본 조립 배선은 후속
- **맥락**: v1.3 C-5는 제외키워드 LABEL도 전 통계 제외(가시성만 차등). `ExcludeKeywordPolicy` 판정·javadoc은 반영했으나, 기준가/알림 표본 조립이 실제로 키워드 히트 딜을 걸러내는 배선은 미구현(딜 제목 접근 필요).
- **잠정값**: `BenchmarkCalculator`·`GetBenchmarkUseCase`·`EvaluateAlertOnDealUseCase`는 아직 outlier·permanentlyExcluded만 제외. 키워드 제외(EXCLUDE·LABEL 공통)는 표본 미적용. deal_event에 제목 컬럼 없음 → deal_event_source→raw_deal_post.title 조인 또는 딜 생성 시 판정 결과 보존 필요.
- **재개 트리거**: 제외키워드 정책(alert_policy.exclude_keywords·global_setting)을 표본 적용할 때 — 딜 생성 시 키워드 히트 여부를 deal_event에 보존(V2 컬럼)하거나 조립 시 제목 로드. 세 집합 분리(B-1, M5)와 함께 정합.

## [열림] Q-29. 세 집합 predicate의 미완 components (keyword-miss·선택축값·배치유보·신선도)
- **맥락**: docs/03 3-1 세 집합 자격 술어 중 DealEvent 필드로 도출 가능한 부분(classified·outlier 3상태·status)만 `DealSets`에 구현. 나머지 components는 상태/데이터 부재로 미포함.
- **잠정값**: `DealSets.pricingSet/occurrenceSet/signalSet`는 (분류·이상치·ENDED)만 필터. **keyword-miss(Q-28)·선택 축값(C-6, 미선택=범위외)·배치유보(PENDING_BATCH, C-4)·signalSet 신선도(3-2 관측시계)**는 미적용 — 각기 딜 제목/변형축값/배치상태/시간좌표가 필요. signalSet 신선도는 SIG(증분4)가 시간좌표로 추가 필터.
- **재개 트리거**: 각 component의 상태가 생기면 순차 편입 — 신선도=증분2 시간좌표 후 SIG, 선택축값=C-6(수요축 모델), 배치유보=백필 배치(C-4), keyword=Q-28. `DealSets` 술어에 `&&` 추가.

## [열림] Q-30. PUR 삭제 3행 매트릭스·백필 유예·수정 규칙 세부 미명시
- **맥락**: docs/15 PUR-01은 "삭제=구매 전 복원(별도 경로, 아카이브 불발동, 상태별 3행 매트릭스)"라 하나 상태별(OBSERVING/REPORT_PENDING/CLOSED) 삭제 허용·부수효과 세부는 미기재. PUR-02 스냅샷 basis 동결의 백필 유예, 수정 규칙(필드별)도 use-case 레벨.
- **잠정값**: 상태기계 전이(순수) + **기록 경로 스냅샷 동결**(PUR-02, `RecordPurchaseUseCase`)까지 구현. 미구현: **삭제 3행 매트릭스**, **수정 규칙**(필드별 재계산), **백필 중 basis 동결 유예**(현재는 기록 즉시 동결), **demandAxisValue SPLIT 필수 검증**(현재 무검증 수용). 상태기계는 삭제와 독립(삭제=엔티티 제거).
- **재개 트리거**: 삭제/수정 REST 착수 시 3행 매트릭스 세부 확인(필요 시 decisions-needed) 후 구현. SPLIT 필수 검증은 variant→product DemandAxisMode 로드 배선 시. 백필 유예는 배치(C-4)와 스냅샷 동결 연동 시.

## [열림] Q-31. PUR-03 상대평가(RELATIVE) 발화 로직은 후속
- **맥락**: PUR-03 상대평가 트리거는 CLOSED·관찰 전 상태에서 "다른 관찰 대비" 발화. 매트릭스 enablement(CLOSED만)는 구현했으나 발화 조건(타 관찰들과의 상대 비교)은 복수 관찰 문맥이 필요.
- **잠정값**: `PurchaseTriggers.enabledFor`가 CLOSED에 RELATIVE 포함. 실제 발화 판정(`paidPriceTriggerFires` 같은 술어)은 미구현 — 상대평가는 "관찰 전·CLOSED만"의 의미(구매 전 비교, 종료 후 회고 비교)가 use-case 문맥(다른 활성/종료 관찰 집합)에 의존.
- **재개 트리거**: PUR 관찰 문맥(PUR-05)·AL 통합 배선 시 — 상대평가 대상(다른 관찰) 정의 확정 후 술어 구현.

_(Q-36. collector DB 적재기 — **해소됨 2026-07-09**: `db/raw_deal_sink.py`. 신규 의존 승인(런타임 `psycopg[binary]`, 테스트 `testcontainers[postgres]`). **업서트 정책 확정**: `(site, post_id)` 충돌 시 변화 필드 전부 갱신(url·title·captured_at·status·headline_price·reaction_score·raw), `posted_at`만 `COALESCE`로 **불변 + 후채움**(C-2). 통합 테스트는 미러가 아니라 **core의 `V1__init.sql`을 직접 적용**해 계약을 검증한다. decision-log 참조. ⚠️ **커서 영속화는 별건** — D-3 선결. 여기서 제거.)_

<details><summary>Q-36 원문 (해소 전 기록 보존)</summary>

## [해소] Q-36. collector DB 적재기(psycopg 어댑터) — 신규 의존·업서트 갱신 정책
- **맥락**: `pipeline/ingest.py`가 `RawDealRecord`(계약 형태)까지는 순수하게 만든다. 남은 것은 이를 `raw_deal_post`에 실제로 쓰는 **IO 어댑터**. core의 `RawDealPostUpserter`가 권위 있는 의미를 준다 — (site, post_id) 자연키로 **업서트**(있으면 갱신, 없으면 삽입, 상태변화 기존행 반영). collector는 현재 DB 의존이 **전무**(순수 파서·파이프라인).
- **잠정값(미착수)**: 아직 안 만듦. 두 가지가 걸려 있어 자율 확정 대신 표시: (1) **신규 의존** psycopg(런타임) + **실 멱등 테스트**용 Testcontainers-python(테스트) — 수집기 첫 DB 발자국. (2) **업서트 갱신 필드 정책**: core 업서터는 충돌 시 url·title·captured_at·status만 refresh하나, collector 레코드는 headline_price·posted_at·reaction_score·raw도 보유 → 이들을 재폴링 때 **갱신할지/삽입 때만 쓸지** 미정(posted_at은 발생시각이라 불변이 자연스러움, C-2). 테스트용 raw_deal_post DDL은 계약 미러(Flyway는 core 단독 소유라 collector는 마이그레이션 금지).
- **재개 트리거**: psycopg·Testcontainers-python 도입 승인 + 업서트 필드 정책 확정 시 — `INSERT ... ON CONFLICT (site, post_id) DO UPDATE`로 core 업서터 의미와 정렬해 구현, Testcontainers-python 멱등 통합 테스트(재삽입 행 불변·상태전이 반영). 이후 scheduler(폴링 루프·백오프·커서)는 실 네트워크라 fetch는 정지조건, 루프/백오프 로직만 fake fetcher로 테스트.
- **진행(2026-07-09)**: 위 문장의 **scheduler 부분은 완료**(`collector/src/collector/scheduler/` — 루프·백오프·커서 순수 구현, fake fetcher 테스트 GREEN). 남은 것은 이 항목의 본체인 **적재 IO**(psycopg 의존 + 업서트 필드 정책)와 실 HTTP fetcher. ⚠️ 커서 영속화를 시작하기 전에 **decisions-needed D-3(차단 사이트 재개 경로)을 먼저 결정**해야 한다 — 안 그러면 차단당한 사이트가 디스크에 영구 중지로 남는다.
- **진행(2026-07-09, 결선 완료)**: **실 HTTP fetcher도 완료**(`scheduler/fetcher.py`, stdlib `urllib` — 신규 의존 0). 레지스트리(`sites.py`)·robots 게이트(Q-38)·종단 스모크(`test_pipeline_smoke.py`)까지 붙어 **fixture 바이트 → fetch → 디코딩 → 파싱 → 정규화 → `RawDealRecord`가 관통**한다. `__main__`은 `COLLECTOR_ALLOW_NETWORK=1` opt-in으로만 실 폴링한다.
- **해소(2026-07-09)**: `db/raw_deal_sink.py` 완성. 남은 것은 **커서 영속화(REL-03)뿐이고 그건 D-3 선결**이라 이 항목과 분리한다.

</details>

## [열림] Q-35. PUR-03 알림 상호작용/게이팅 정책 미확정 (paidPrice 트리거만 배선)
- **맥락**: `EvaluateAlertOnDealUseCase`가 활성(OBSERVING) 관찰의 `paidPrice` 하회 트리거만 AL에 가산(서열 최하위 PAID_PRICE). PUR-03 표의 나머지 상호작용은 **정책 미확정**이라 손대지 않음: (a) variant 등록 알림(🔥/목표가)이 구매 관찰 상태에 의해 **게이팅되는지**(예: ARCHIVED면 억제), (b) variant 등록 알림과 구매 관찰 알림의 **결합/OR 관계**, (c) 상대평가(Q-31). 현 구현은 등록 알림은 그대로 두고 paidPrice만 순수 가산 — 되돌리기 쉬운 보수적 선택.
- **잠정값**: paidPrice 하회(활성 관찰, "<" 경계, OR) = PAID_PRICE 강도 추가. 상태 게이팅·결합·ARCHIVED 억제는 미적용(등록 알림 무변).
- **재개 트리거**: 알림 상호작용이 **기획 정책 결정**(decisions-needed 대상)으로 확정되면 반영 — 특히 "구매 후 등록 알림을 상태로 억제할지". 실사용(텔레그램 발송) 후 필요성 확인 권장.

## [열림] Q-32. 성적표 capturedAt ≤ 발급(지각 백필 제외)은 DealEvent capturedAt 부재로 후속
- **맥락**: PUR-04 산입은 "firstSeen ∈ 관찰기간 AND **capturedAt ≤ 발급**". 후자는 발급 후 지각 백필된 딜을 성적에서 제외하는 규칙인데, 도메인 `DealEvent`엔 capturedAt이 없다(raw_deal_post에만). docs/03 3-2는 capturedAt "항상 별도 보존"을 요구.
- **잠정값**: `ReportCardCalculator`는 firstSeen ∈ 관찰기간 ∩ observedFrom만 적용. capturedAt ≤ 발급은 미적용(지각 백필 미제외). percentile·최저 기회·UNOBSERVED·게이트는 완성.
- **재개 트리거**: DealEvent에 capturedAt 필드 추가(B-2 시간좌표 완성) 시 — 산입에 capturedAt ≤ 발급 필터 추가. 배치유보(C-4)와 함께 정합.

## [열림] Q-33. DIGEST 섹션 조립·저장 원자성·발송은 배선/블록
- **맥락**: docs/18 DIGEST는 대부분 스케줄 발송·저장물 원자성(REL-03)·분할 발송·섹션 조립이며, 실 발송은 제3계열(텔레그램)이라 키 미발급 블록. 순수 도메인(창·플로우 귀속·전환 억제·조용한 주)만 구현.
- **잠정값**: `DigestWindow`·`DigestRules`(전환·조용한주)만 완성. 섹션 6종 조립(①~⑥), 저장물 발송 성공 후 갱신(원자성)·분할 연번, 배치 중 "수치 잠정" 병기, 관측 공백 헤더는 미구현. ④(핀 결말)은 [WATCH-유보] 종속(M6). CAD 미포함·중고 축 기각은 준수.
- **재개 트리거**: 텔레그램 봇 토큰 발급 + M5 배선(스케줄러) 착수 시 — 저장물 지속·섹션 조립·발송 원자성 구현. ④는 WATCH(M6) 채택 후.

## [열림] Q-34. SIG·CAD 조회의 observedFrom·lastPoll = 잠정 대체값(미저장)
- **맥락**: SIG·CAD REST 배선(`GetSignalUseCase`·`GetCadenceUseCase`, compute-on-demand)은 관측 좌표 2개가 필요하다 — CAD의 `observedFrom`(variant 관측 시작)과 SIG 신선도의 `lastPoll`(마지막 성공 폴링). 둘 다 아직 어디에도 저장하지 않는다(등록/수집 메타 미배선).
- **잠정값**: `observedFrom` = 해당 variant 딜 중 **최초 firstSeen**(딜 0건이면 now) — 관측 개시를 최초 관측으로 근사. `lastPoll` = **`clock.instant()`(now)** — 항상 방금 폴링했다고 가정(신선도가 낙관적으로 편향). 가장 보수적이진 않으나 조회 read-model이라 되돌리기 쉬움. seam = 두 use-case의 해당 라인 1곳씩.
- **재개 트리거**: (a) variant 등록/백필 도달 시각을 저장(REG 배선)하면 `observedFrom`을 그 값으로 교체 — 최초 딜보다 이른 관측 공백을 반영. (b) 수집 파이프라인이 `last_successful_poll`(사이트별/전역)을 기록하면 `lastPoll`을 실측으로 교체 — 수집 정지 시 신선도가 올바로 강등(Q-25). 연결: `Staleness`(3-2 관측 시계).
- **진행(2026-07-09)**: collector 스케줄러가 `SiteState.last_successful_poll`을 **산출**하기 시작했다(사이트별). 다만 아직 **메모리 값**이라 core가 읽을 수 없다 — 영속화는 Q-36(DB 접점)에 종속. Q-36 해소 시 이 값을 테이블에 쓰면 (b)가 바로 열린다.
- **⚠️ 정정(2026-07-10)**: 위 문장은 이제 틀렸다. **Q-36은 해소됐지만 (b)는 열리지 않았다.** ① 값을 담을 테이블이 없고(마이그레이션 = core 소유) ② 그 값을 읽어 `SignalCalculator`에 넘기는 곳은 `GetSignalUseCase`(**상대 소유 기존 파일**)다. 그래서 지금도 `lastPoll = clock.instant()`이고, **SIG-02 신선도 3단은 "확신" 한 칸만 도달 가능하다** — 수집이 멈춰도 신호등이 강등되지 않는다. 재개 트리거는 "무엇이 참이 되어야 하는가"로 다시 쓴다: **`last_successful_poll`이 어딘가에 영속되고, `GetSignalUseCase`가 그것을 읽을 수 있어야 한다.** (2026-07-10 감사에서 `SIG-02`가 코드 참조 0으로 걸려 발견.)

---

_(이하 2026-07-09 collector 스케줄러(폴링 루프·백오프·차단 중지) 착수에서 등장.)_

## [열림] Q-37. 지수 백오프 수치(base·factor·cap) — 미승인 잠정 파라미터
- **맥락**: `docs/11` line 4가 "지수 백오프"를 요구하나 수치는 어디에도 없다(docs/31 승인 6값에 없음). 폴링 실패 재시도 간격을 결정.
- **잠정값**: `base=60s, factor=2, cap=30min`. `BackoffPolicy`(frozen dataclass)로 **주입** — 순수 함수는 주입값만 참조하고 모듈 상수에 하드코딩하지 않는다(Q-14 `absurdityRatio` 선례와 동일: 승인값과 미승인값을 섞지 않음). 지터 없음(1인용·사이트당 1커넥션이라 thundering herd 무관, 테스트 결정성 우선).
- **재개 트리거**: 운영자 승인 요청(docs/31 위임 수치 표에 등재됨) → 승인 시 상단 표 편입. 실 수집에서 5xx 빈발·복구 지연 관측 시 폭 재조정.

_(Q-38. robots.txt 준수 게이트 — **해소됨 2026-07-09**: `scheduler/fetcher.py`의 `RobotsGate`로 구현. `urllib.robotparser`(stdlib) — 신규 의존 0. 호스트별 1회 조회 후 캐시, 404면 전체 허용(표준). **Disallow를 `403`으로 매핑**해 `classify_status`가 BLOCKED로 보게 했다 → 사이트 자동 중지 + Alert, 재시도 없음(접근 금지를 일시 장애로 격하하지 않는다). **Crawl-delay가 우리 하한보다 길면 따르고 짧으면 우리 하한이 이긴다**(SEC-08 "설정으로 완화 불가"). ⚠️ **실 robots.txt와 대조하지 않았다** — fake opener 테스트만. 실 수집 착수 시 pre-deploy §F에서 확인. 여기서 제거.)_
　　↳ **정정 2026-07-10**: 위 굵은 글씨 중 **Crawl-delay 부분은 거짓이었다.** `effective_interval_with_robots`는 존재하고 GREEN이었지만 **프로덕션 호출자가 0**이었다 — `run_cycle`은 우리 하한만 봤다. 뽐뿌가 `Crawl-delay: 120`을 선언해도 60초마다 두드렸을 것이다. `__main__._interval_port`를 `run_cycle(interval_for=...)`로 주입해 실제로 배선했고, **배선을 지우면 RED가 되는 회귀 테스트**를 달았다(`test_declared_crawl_delay_actually_throttles_the_polling_loop`). Disallow(자동 중지)는 `HttpFetcher.__call__`이 부르므로 처음부터 살아 있었다. → `docs/99` 2026-07-10.

_(Q-41. `parse_bunjang`의 `status="ENDED"` 불일치 — **해소됨 2026-07-09**: `SOLD_OUT`으로 수정. 종단 스모크가 없어 3일간 잠복했던 계약 위반(`to_raw_records`가 `ValueError`). ⚠️ **잔류**: 번개 status 코드표는 여전히 미실측(`"0"=판매중`만 안다). 비-`"0"`을 전부 SOLD_OUT으로 보는 건 잠정이며 `예약중`을 판매완료로 오독할 수 있다 → 아래 Q-44로 좁혀 유지. 여기서 제거.)_

## [열림] Q-44. 번개장터 status 코드표 미실측
- **맥락**: `docs/98`은 `status`("0"=판매중 등)만 기록한다. 나머지 코드(예약중·판매완료·삭제)가 각각 무엇인지 모른다.
- **잠정값**: `parse_bunjang`은 `"0"→ACTIVE`, **비-"0" 전부 →`SOLD_OUT`**. 예약중 매물을 판매완료로 오독할 수 있다(중고 생애주기 알림이 조기 발화). 파서 docstring에 명시.
- **재개 트리거**: M2(중고) 착수 시 — 번개 검색 응답을 여러 상태로 실측해 코드표를 `docs/98`에 기록하고, `예약중`을 ACTIVE로 되돌릴지 결정. `used_listing_observation` 생애주기 판정(docs/14)과 함께.

_(Q-39. BLOCKED 자동 중지의 수동 재개 경로 — **`working-area/decisions-needed.md` D-3으로 승격 2026-07-09**: SEC-08 "재시도 강행 금지"의 해석이 걸려 잠정값으로 진행할 수 없다(정책 결정). **Q-36(커서 영속화) 착수 전 필수 선결.** 여기서 제거.)_

_(Q-40. REL-06 파싱 드리프트 감지 — **해소됨 2026-07-09**: `scheduler/drift.py`(순수, 이동창). 두 신호를 본다 — ① **조용한 0건**(성공인데 연속 0건 = 구조 변경의 전형. 뽐뿌 셀렉터 체인이 끊겼을 때 예외 없이 0건이었다) ② **성공률 저하**(창 안 TRANSIENT 비율). BLOCKED는 세지 않는다(이미 중지+Alert). 창 미충족 시 미판정, 회복 시 재무장, 같은 증상 반복 알림 억제. `__main__`이 사이클마다 관측을 먹인다. 임계는 **미승인 잠정 주입**(아래 Q-45). 여기서 제거.)_

_(Q-47. web 등록 폼 가격축 조합 — **해소됨 2026-07-09**: `buildCommand`가 데카르트 곱을 만든다(용량 2 × 색상 2 → variant 4). 축 이름 중복은 거부(맵에서 덮어쓰기), 빈 축 행은 무시, 화면이 "생성될 variant N개"를 미리 보여준다. 여기서 제거.)_

## [열림] Q-62. PUR 상태기계 — 만료는 배선했다. **CLOSED·ARCHIVED로 가는 길이 없다**
- **맥락**: `PurchaseState`는 `OBSERVING → REPORT_PENDING → CLOSED → ARCHIVED → OBSERVING`을 정의하고 `Purchase.expire()/close()/archive()/reactivate()`가 다 있다. 그런데 **프로덕션에서 상태를 쓰는 곳은 `RecordPurchaseUseCase`(항상 OBSERVING) 하나뿐이었다.** 상태기계에 전이가 있어도 부르는 사람이 없으면 그 전이는 존재하지 않는다.
- **해소된 부분(2026-07-10)**: `ExpirePurchaseObservationsUseCase`(신규 파일) — 관찰 기간이 끝난 구매를 `REPORT_PENDING`으로. `PipelineScheduler`가 **ingest보다 먼저** 부른다(ingest가 알림을 태우는데 PUR-03 "산 뒤 알림"은 OBSERVING에만 발화하므로). 스모크 5-2b가 종단 증명. 그전까지 ① "관찰 N일차"가 무한히 커지고 ② **3년 전 구매에도 계속 알림이 나갔을 것**이며 ③ 성적 집계 대기로 넘어가지 않았다.
- **남은 것**: `REPORT_PENDING → CLOSED`는 **성적표 발급(PUR-04)**이 선행한다 — 성적표를 담을 테이블도, 발급 유스케이스도 없다(`ReportCardCalculator`는 순수 도메인만). `CLOSED → ARCHIVED`(PUR-06)와 `ARCHIVED → OBSERVING`(재활성) 역시 호출자가 없다. 즉 **구매는 REPORT_PENDING에서 영원히 멈춘다.** 그 상태의 관찰 문맥은 "성적 집계 중"이라 화면은 정직하지만, 집계는 아무도 하지 않는다.
- **잠정값**: REPORT_PENDING까지만. 만료된 구매는 알림 트리거에서 빠진다(의도). **화면 문구는 "성적 집계 중"이 아니라 "관찰 종료 · 성적표는 아직 발급되지 않습니다"다**(2026-07-10) — "집계 중"은 **진행 중**이라는 뜻인데 집계하는 코드가 없다. 기다리면 나온다고 믿게 두는 것이 과대약속이다(절대 원칙 6). `docs/15` PUR-05의 문구와 다르지만, 그 문구는 **집계가 실제로 일어난다는 전제** 위에 있다. 발급이 배선되면 되돌린다(seam = `purchase/present.ts` 두 줄).
- **재개 트리거**(무엇이 참이 되어야 하는가): ① 성적표를 담을 저장소가 있어야 한다(마이그레이션 — core 소유) ② 발급 시점 규약이 정해져야 한다(Q-32: `DealEvent.capturedAt` 부재로 "capturedAt ≤ 발급" 검증 불가) ③ 아카이브 조건("다른 활성 관찰 없을 때")을 판정할 곳이 정해져야 한다. ④ 삭제 3행 매트릭스(Q-30)와 정합.

## [열림] Q-61. SEC-03·SEC-04·SEC-07이 어느 보드에도 없었다 (Q-58과 같은 실패 모드)
- **맥락**: `docs/20`의 NFR ID 26개를 코드·테스트·스크립트·compose·CI에서 전수 grep했더니 **SEC-03·SEC-04·SEC-07이 참조 0**이었고, `docs/91`·`working-area` 어디에도 없었다. 보드에 없는 요구는 없는 일이 된다 — PERF·OPS에서 이미 한 번 겪었다(Q-58).
- **SEC-07 개인정보 최소화 — 지켜지고 있었으나 강제되지 않았다 → 해소(2026-07-10)**: 번개 응답에는 `uid`(판매자 식별자)·`location`(동 단위 주소)·`imp_id`(광고 추적자)가 온다. `parse_bunjang`은 불리언 셋만 담는다 — **신중해서 그랬을 뿐 계약이 아니었고**, `raw`는 `jsonb`라 한 줄이면 응답 전체가 들어간다. `tests/test_privacy.py`가 golden 전수로 잠갔다: 키 허용집합 + **값** 검사(키 이름을 `u`/`loc`으로 바꿔 우회해도 잡힌다). 뮤테이션으로 실제 FAIL 확인.
- **잠정값(SEC-03)**: 텔레그램 봇 chat_id 화이트리스트·인라인 콜백 서명 검증은 **미구현**. 봇 어댑터 자체가 없다(Q-20, 토큰 미발급). 지금 순수 판정만 만들어 두는 것은 투기다 — 어댑터와 함께 만든다.
- **잠정값(SEC-04)**: extension ingest(고정 토큰·스키마 검증·레이트 리밋)는 **기능4** 범위. 확장이 없으므로 노출면도 없다.
- **재개 트리거**: SEC-03 — 텔레그램 어댑터가 존재하는 순간. 그 커밋에 화이트리스트·콜백 검증이 **함께** 들어가야 한다(`pre-deploy §A`가 `TELEGRAM_ALLOWED_CHAT_IDS`를 필수로 요구한다). SEC-04 — 크롬 확장이 존재하는 순간.

## [열림] Q-60. `guard.sh`는 스크립트 파일 안의 네트워크 호출을 보지 못한다
- **맥락**: `.claude/hooks/guard.sh`(PreToolUse)는 **Bash 명령 문자열**을 파싱해 `curl`/`wget` + 6개 대상 호스트를 차단한다. 그런데 `bash scripts/check-robots.sh`처럼 **스크립트를 실행하는 명령**에는 호스트도 `curl`도 나타나지 않는다. 훅은 그 안을 보지 않는다.
- **왜 이제 드러났나**: 2026-07-10 `scripts/check-robots.sh`(실 robots.txt 대조 도구)를 만들면서. 이 스크립트는 정의상 실 사이트로 나간다.
- **왜 훅을 고치지 않나**: 스크립트 내용을 파싱해 차단하려면 훅이 파일 시스템을 읽고 셸 문법을 이해해야 한다. **오차단이 조용히 작업을 마비시킨다**는 규칙상, 그런 휴리스틱은 이 프로젝트의 다른 규칙과 충돌한다. 훅은 결정론적인 것만 막는다.
- **잠정값 (다층 방어)**: ① 네트워크로 나가는 스크립트는 **자기 자신에게도 opt-in 게이트를 건다**(`ALLOW_REAL_ROBOTS=1`, collector의 `COLLECTOR_ALLOW_NETWORK`와 같은 패턴). ② 스크립트 상단에 "에이전트가 이걸 opt-in과 함께 돌리면 정지조건 위반"임을 명시. ③ 정지조건은 결국 **지침의 몫**이다 — 훅은 실수를 막고, 고의를 막지는 못한다.
- **재개 트리거**: 네트워크로 나가는 스크립트가 늘어나 opt-in 패턴이 흔들리면 — 그때 훅이 `bash *.sh` 실행을 **전부** 사람에게 확인받게 할지 검토(오차단 비용이 크다). seam = `.claude/hooks/guard.sh` 1곳.

## [열림] Q-59. REL-03 폴링 커서 영속화 — 추적할 자리가 없었다
- **맥락**: `SiteState`(연속 실패 횟수·`stopped` 플래그·`next_attempt_at`)가 **메모리에만** 있다. 컨테이너가 재시작하면 초기화된다. REL-03은 "재시작 내성: 폴링 커서 영속화"를 요구한다.
- **왜 이 항목이 지금 생겼나**: 2026-07-10 전수 감사에서 발견 — `decisions-needed` D-3과 `pre-deploy §F`가 이걸 **"Q-36(커서 영속화)"**라고 불렀는데, Q-36은 사실 **"collector DB 적재기"**였고 이미 해소됐다. 즉 REL-03을 추적하는 항목이 어디에도 없었다.
- **막고 있는 것은 DB가 아니다.** 적재기(`db/raw_deal_sink.py`)는 이미 있다. 진짜 선결 조건은 **차단당한 사이트의 재개 경로**(`decisions-needed` D-3)다 — 그 결정 없이 커서를 디스크에 남기면 `stopped=True`가 영구히 굳어 사이트가 죽는다.
- **잠정값**: 메모리 상태 유지. 재시작하면 커서가 초기화되고, 그 덕에 차단된 사이트가 **우연히** 재개된다(설계가 아니라 사고다). `restart: on-failure`라 정상 종료 시엔 재시작하지 않으므로 이 우연도 보장되지 않는다.
- **재개 트리거**: **D-3 확정 후.** 재개 경로가 정해지면 `SiteState`를 테이블 하나(`site_poll_state`)에 저장한다 — Flyway는 core 단독 소유이므로 **컬럼 추가는 상대와 조율**. seam = `collector/src/collector/scheduler/policy.py`의 `SiteState` + `advance()` 1곳.

## [열림] Q-58. PERF-01~04·OPS-01이 어느 보드에도 없었다 (측정 자체가 없다)
- **맥락**: 2026-07-10 요구 문서 전수 대조에서 발견. `docs/20`의 다음 요구는 코드에도 보드에도 대응물이 없다.
- **PERF-03**(웹 API p95 ≤ 500ms): **측정하지 않는다.** 기준가는 매 요청 재계산이고 캐시가 없다(`@Cacheable` 0건). 표본이 작아 지금은 빠르지만 아무도 재고 있지 않다.
- **PERF-04 후반**(기준가 증분 재계산 또는 요청 시 계산+캐시): 캐시 없음. 문서가 "1인용 규모에서 과최적화 금지"라 했으니 **의도된 선택으로 볼 수 있으나 어디에도 그렇게 적혀 있지 않았다.** 인덱스는 충족(`idx_deal_event_variant_seen`, `raw_deal_post` unique).
- **PERF-01**(알림 지연 p95 ≤ 폴링주기+30초): 텔레그램 발송 자체가 스텁(Q-20). 측정 불가.
- **PERF-02**(네이버 쿼터 50% 이하 + 캐시 1h): 네이버 어댑터가 스텁(Q-3). 측정 불가.
- **OPS-01 후반**(환경별 `.env` 분리 local/prod): `.env.example` 하나뿐. 운영 `.env`는 사람이 EC2에서 만든다(pre-deploy §B).
- **잠정값**: 전부 현 상태 유지. 기준가는 요청 시 재계산(캐시 없음), 응답 시간은 측정하지 않는다.
- **재개 트리거**: PERF-03/04는 **실 데이터가 유입돼 딜이 수백 건 쌓인 뒤** 측정한다 — 그 전의 캐시는 과최적화다(문서가 금지). PERF-01/02는 토큰·키(Q-20·Q-3). OPS-01은 운영 배포 시 `.env.prod` 작성(pre-deploy §B로 이미 추적 중).

## [열림] Q-57. core는 구조화 로그(JSON)를 내지 않고, 카운터도 절반뿐이다
- **맥락**: OBS-01은 "구조화 로그(JSON)", OBS-02는 "핵심 카운터: 수집 글 수, 매칭 CONFIRMED/CANDIDATE/REJECTED 비율, 병합률, 알림 발송 수, 큐 적체, API 쿼터 사용량"을 요구한다. **어느 보드에도 없던 요구다**(2026-07-10 발견).
- **한 것(2026-07-10)**: `PipelineScheduler`가 매 틱 `PipelineTickReport`를 남긴다 — `postsLinked·dealsCreated·merged·queued·ended·pending·rawTotal`. 병합률은 "링크는 늘었는데 딜은 안 늘었다"로 유도한다. 0을 생략하지 않는다. 스모크가 `dealsCreated=1 merged=0 pending=0`을 실제 로그에서 확인한다.
- **~~① JSON이 아니다~~ 해소(2026-07-10)**: Spring Boot 4.1 내장 구조화 로그를 **환경변수로만** 켰다 — `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(compose `CORE_LOG_FORMAT`, 기본 `ecs`). `application.yml`도 `logback-spring.xml`도 만들지 않았다(core 파일 무수정). 실측 출력: `{"@timestamp":…,"log":{"level":"INFO","logger":"…PipelineScheduler"},"message":"pipeline tick postsLinked=1 …","ecs":{"version":"8.11"}}`. 스모크 5-1b가 매번 확인한다(형식은 조용히 되돌아간다). collector의 JSON Lines와 같은 창구에서 읽힌다.
- **남은 것**: ② **매칭 tier 비율**(CONFIRMED/CANDIDATE/REJECTED)은 `IngestDealsUseCase`가 void라 밖에서 셀 수 없다 — 큐 증가분으로 CANDIDATE+UNKNOWN만 근사한다. ③ **알림 발송 수**는 `StubAlertSender` 안에 있다. ④ **API 쿼터**는 네이버 키 대기(Q-3). ②③은 core 기존 파일 수정이라 상대와 조율.
- **잠정값**: 텍스트 로그 + 틱 단위 카운터. `docker logs`로 읽는다.
- **재개 트리거**: ①은 로그 수집기(운영 배포)를 붙일 때 — core 전체 로그 형식을 바꾸는 일이라 상대와 조율. ②③은 use case 반환값 변경이 필요하니 **core 기존 파일 수정**이라 조율 대상. ④는 Q-3.

## [열림] Q-56. 파이프라인 단계 실패가 로그에만 남는다
- **맥락**: `PipelineScheduler.runStep`은 한 단계(ingest·reprocess)가 던져도 다른 단계와 다음 주기를 살리려고 예외를 잡는다. 잡은 뒤에는 `log.error`로 단계 이름과 함께 남긴다 — 그게 전부다.
- **왜 위험한가**: DB 스키마 불일치·낙관적 락 충돌 같은 지속적 실패가 나면 **파이프라인은 도는 척하면서 아무것도 처리하지 않는다.** `docker logs`를 보지 않으면 모른다. collector의 `giving_up`(연속 실패 시 프로세스 종료)과 달리 core는 스스로 내려오지도 않는다.
- **잠정값**: `log.error`만. 연속 실패 카운터도, 관리 알림도 없다. 1인용이고 실 폴링 전이라 지금은 수용.
- **재개 트리거**: 텔레그램 봇 토큰 발급(Q-20) → 관리 알림 chat(OBS-03)이 생기면 collector의 `giving_up`·`sink_error`와 **함께** 흘린다. 그때 연속 실패 임계도 같이 정한다(collector의 `SINK_FAILURE_LIMIT=3`과 정합). seam = `PipelineScheduler.runStep` 1곳.

_(Q-55. SEC-05 크기 상한 — **해소됨 2026-07-10**: `pipeline/ingest.py`에 상한 4종(title 300자 · url 2000자 · post_id 64자 · raw 256KiB, **바이트**로 잼). **자르지 않고 거절**한다 — 잘린 제목은 정상 제목의 얼굴을 한 거짓말이라 매칭(BM-03)을 조용히 망친다. 한 건이 비대해도 배치 전체를 버리지 않는다(원칙 3). 무엇을 왜 버렸는지 `oversized` 이벤트로 남기고 `cycle.skipped` 카운터에 0도 센다. 상한값 근거: golden 89건 실측 최대(title 62 · url 75 · post_id 11 · raw 57B)의 수 배 — 전수 대조로 오차단 0건 확인. 잠정값이며 seam은 그 상수 4개. 여기서 제거.)_

## [열림] Q-54. 적재에 실패한 사이클의 딜은 버려진다 (재시도 큐 없음)
- **맥락**: `sink.upsert_all`이 던지면(DB 재시작·연결 끊김) 그 사이클에 파싱한 딜은 **메모리에서 사라진다**. 프로세스는 `sink_error` 이벤트로 유실 건수를 남기고 다음 틱을 계속한다.
- **왜 대체로 괜찮은가**: 15초 뒤 같은 목록 페이지를 다시 긁으므로 그 딜들은 **다시 잡힌다**. 목록에서 밀려날 만큼(수 페이지) 트래픽이 몰린 사이에만 영구 유실이다. 게시판당 1req/min 하한을 감안하면 실제 창은 수 분이다.
- **잠정값**: 재시도 큐·디스크 버퍼 없음. 연속 3회 실패하면 프로세스가 exit 1로 내려온다(`SINK_FAILURE_LIMIT`) — 유실이 누적되기 전에 멈추는 쪽을 택했다.
- **재개 트리거**: 1차 검증에서 `sink_error`가 실제로 관측되면 — 그때 유실 건수와 재폴링 회복률을 보고 판단한다. 커서 영속화(REL-03, D-3 선결)를 하게 되면 실패한 배치를 디스크에 남기는 것도 같이 검토.

## [열림] Q-53. `currentPrice = 0`이 "미확립"과 "공짜"를 구분하지 못한다
- **맥락**: `StubCurrentPriceProvider`(core)는 네이버 키 미발급 상태에서 현재가로 **0**을 반환한다(Q-3). `BenchmarkCalculator.leg()`는 `won = currentPrice - reference`를 그대로 계산하므로, 기준가 820,000원이면 API가 `gap.vsBenchmark = {won: -820000, pct: -100.0}`을 내보낸다. **타입만 보면 "지금 100% 싸다"는 정상 응답이다.**
- **왜 위험한가**: 갭은 이 시스템의 결론이다. 표시 계층이 이 값을 그대로 그리면 "지금 사라"는 가장 강한 신호를 거짓으로 낸다(절대 원칙 1·6). SPARSE/NONE의 통계 필드를 `null`로 못박은 것과 같은 종류의 문제인데, **현재가만 sentinel(0)로 남아 있다**.
- **잠정값**: **web이 방어한다.** `web/src/decision/present.ts`의 `CURRENT_PRICE_UNAVAILABLE = 0` 한 곳에서 해석하고, `gapLine`이 갭 대신 "현재가 미확립 — 네이버 키 없음"을 낸다(테스트로 못박음). core는 손대지 않았다(상대 영역).
- **왜 임시인가**: 방어가 **소비자 쪽에** 있다. 다른 소비자(텔레그램 알림, 크롬 확장)가 생기면 각자 0을 알아야 한다.
- **알림 경로는 증상이 반대다(더 조용해서 더 나쁘다)**: `AlertEvaluator:54`의 콜드스타트 잭팟은 `price ≤ currentPrice·(1−ratio)`로 판정한다. `currentPrice=0`이면 임계가 0이라 **어떤 가격도 통과하지 못한다.** 즉 오알림이 아니라 **놓침**이고, 하필 그 경로는 **기준가가 아직 없는 초기**(=지금)를 위해 만든 것이다. 절대 원칙 3(놓침 > 오알림)에 정면으로 어긋나지만 아무 로그도 남지 않는다. 발송(Q-20)이 켜지기 전에 처리해야 한다.
- **재개 트리거**: 네이버 키 발급(Q-3) 시 실 어댑터로 교체하면 자연 소멸. 그 전에 알림 발송(Q-20)이 켜지면 **먼저 core에서** `currentPrice`를 `Long`(nullable)이나 `Optional`로 바꾸고 `Gap`을 `null`로 내는 게 맞다 — 상대와 조율. 그때 web은 상수 하나와 `gapLine`만 고치면 된다.

## [열림] Q-52. OBS-01 상관 ID(딜 단위 추적)가 없다
- **맥락**: OBS-01은 "구조화 로그(JSON), **상관 ID(딜 단위 추적)**"를 요구한다. collector는 이제 JSON Lines로 사이클·사이트 단위 카운터를 낸다(`observability.py`). 그러나 개별 딜이 수집→매칭→병합→알림을 거치며 어떤 판정을 받았는지 추적할 ID가 없다.
- **잠정값**: collector는 **사이클·사이트 단위**까지만 관측한다(`sites_polled`·`deals`·`by_site`·`failures`·`blocked`·`alerts`·`stopped_sites`). 딜 단위 추적은 `deal_event`를 만드는 core의 관심사다. 자연키 `(site, post_id)`가 사실상 상관 ID 역할을 하지만 로그에 싣지는 않는다(사이클당 수십 줄이 된다).
- **재개 트리거**: 1차 검증에서 "이 딜이 왜 알림이 안 왔나"를 추적해야 할 때 — core가 `deal_event.id`를 상관 ID로 삼아 매칭·병합·알림 판정 로그를 잇는다. collector는 `raw_deal_post.id`를 반환받아 로그에 실을 수 있다(현재 sink는 건수만 돌려준다).

_(Q-51. REL-05 롤백 스크립트 — **해소됨 2026-07-09**: `R2__purchase_rollback.sql` 작성 + `scripts/rollback-drill.sh`(CI `rollback` 잡). 일회용 컨테이너에서 ① 모든 V에 짝 R이 있는지 ② 전진 → 역순 후진 시 public 스키마가 비는지 ③ **순서를 어기면(R1 먼저) 외래키 의존으로 실패하는지** ④ 롤백 뒤 재전진이 되는지를 매 커밋 확인한다. R2를 숨겨 드릴이 실제로 FAIL하는 것도 확인했다. decision-log 참조. 여기서 제거.)_

_(Q-50. OBS-04 전용 헬스 엔드포인트 — **해소됨 2026-07-10**: `adapter/web/HealthController` + `HealthReport`(**신규 파일 2개**, core 기존 파일 무수정). `GET /api/v1/health` → `{"status":"UP","components":{"db":{"status":"UP"}}}`, DOWN이면 **503**. ① 커넥션 유효성만 확인해 딜이 수만 건이어도 상수 시간 ② 어느 컴포넌트가 죽었는지 지목 ③ 경로를 `HealthEndpointTest`가 못박아 조용히 깨지지 않는다. **이 항목의 재개 트리거가 틀렸다** — "actuator 추가(=core 기존 파일)"를 유일한 길로 적어 두는 바람에 우리 레인의 일감이 상대 몫으로 잘못 분류돼 있었다(교훈 99). 두 가지를 실 스택 드릴로 확인했다: DB만 죽여도 core는 **살아서 503을 준다**(smoke 0-1), 그리고 응답이 **접속 정보를 흘리지 않는다**(예외 메시지 대신 타입 이름만 — JDBC 예외 메시지는 URL·사용자명을 담는다). 곁가지 2건: Hikari `connectionTimeout` 기본 30초라 DB가 죽으면 헬스가 30초 매달린다 → compose에서 3초로 좁혔다. `PipelineScheduler`의 스냅샷 조회가 `runStep` 밖에 있어 DB 단절 시 **단계가 한 번도 시도되지 않았다** → 격리 + 부재 시 무보고. 여기서 제거.)_

## [열림] Q-49. `POST /api/v1/products`에 서버측 검증이 없다 — 잘못된 입력이 500이 된다
- **맥락**: `RegistrationController`는 `@Valid`를 쓰지 않고 `RegisterProductCommand`에도 컴팩트 생성자 검증이 없다(`spring-boot-starter-validation`은 의존성에 있으나 미사용). 빈 `name`으로 POST하면 `ProductEntity.name nullable=false` 제약에 걸려 **`DataIntegrityViolationException` → 500**이 난다. `axes`/`variants`가 `null`이면 `NullPointerException` → 500.
- **왜 발견됐나**: web 최소 슬라이스가 `docs/benchmark/07`의 "FE code별 분기 확정 기재" 의무를 이행하며 드러났다. 클라이언트(`buildCommand`)가 먼저 검증하므로 화면으로는 안 보이지만, **curl로 직접 치면 500**이다. 클라이언트 검증은 방어가 아니라 편의다.
- **잠정값**: 미수정. web이 클라이언트 검증으로 가린다. `ApiExceptionHandler`는 이 예외들을 매핑하지 않으므로 `{code,message}`도 못 준다 → 클라이언트는 `HTTP_500`으로 처리.
- **재개 트리거**: `RegistrationController`/`RegisterProductCommand`는 **기존 core 파일**이라 상대와 조율 대상. 착수 시 — `@Valid` + `@NotBlank`/`@NotEmpty` 또는 record 컴팩트 생성자 검증, `MethodArgumentNotValidException`·`IllegalArgumentException`을 `ApiExceptionHandler`에 400으로 매핑(신규 code 필요, 예: `REG_INVALID_COMMAND`).

## [열림] Q-48. 알림 정책(REG-03) — REST는 생겼으나 **엔티티가 매핑하지 않는 필드 셋**이 남았다
- **해소된 부분(2026-07-10)**: `AlertPolicyController`(GET/PUT `/api/v1/variants/{id}/alert-policy`) + `AlertPolicySettingsUseCase` + `AlertPolicySettings`(순수 검증) + `InvalidAlertPolicyException` + `AlertPolicyExceptionHandler` — **전부 신규 파일**, core 기존 파일 무수정. 이전까지 `alert_policy`에는 **프로덕션 writer가 없었다** — `EvaluateAlertOnDealUseCase`가 읽기는 했으나 행이 영원히 생기지 않아 확정본 §107의 "OR [사용자 목표가 이하]" 트리거와 방해금지(AL-04)가 발화할 수 없었다. 스모크 5-1d가 `intensity=TARGET`으로 정책이 판정에 닿는 것을 증명한다.
- **남은 것**: `AlertPolicyEntity`가 `k_display`·`exclude_keywords`·`demand_axis_filter`를 **매핑하지 않는다**(⚠️라벨 토글도 컬럼 부재). 그래서 REG-03의 6개 항목 중 넷(targetPrice·기간 P·quiet hours 2개)만 저장된다. 갱신은 **벌크 UPDATE**라 미매핑 컬럼을 보존한다(delete+insert였다면 DB 기본값으로 되돌아가 매핑을 붙이는 날 데이터가 사라진다 — `updatePreservesColumnsTheEntityDoesNotMap`이 못박는다).
- **또 하나**: 미설정 variant의 GET은 `periodMonths: null`을 낸다. 알림 판정이 쓰는 기본 6개월은 `EvaluateAlertOnDealUseCase`의 **private 상수**라 어댑터가 읽을 수 없다. 지어내 채우면 그 값이 세 번째 사본이 되고 사본은 드리프트한다.
- **잠정값**: 위 넷만. web 화면은 붙였다(`policy/AlertPolicyPanel`, 판단 화면 안 — "지금은 아니다"의 다음 행동이 "그럼 얼마면 알려줘"라서). **없는 손잡이는 그리지 않는다** — 그리면 저장되는 줄 안다. 미설정이면 "목표가 알림은 발화하지 않습니다"라고 말하고, 판정 기간의 시스템 기본값은 **숫자로 말하지 않는다**(과대약속 금지 + 세 번째 사본 금지).
- **재개 트리거**: ① `AlertPolicyEntity`에 세 컬럼이 매핑되어야 한다 ② `DEFAULT_PERIOD_MONTHS`가 한 곳에서 소유되어 어댑터도 읽을 수 있어야 한다 ③ 예외 핸들러가 한 곳으로 합쳐져야 한다. 셋 다 **core 기존 파일**이라 상대와 조율. (구현 수단은 여러 가지다 — 위 셋은 "무엇이 참이 되어야 하는가"이지 방법이 아니다.)

## [열림] Q-46. 🔴 조건 태그가 `deal_event`에 도달하지 않는다 — 조건부 가격이 무조건 가격처럼 표본에 들어간다
- **맥락**: BM-02(`docs/11`)는 "저장 기준 = 실결제가 + 배송비, **카드·쿠폰 역산 금지(as-posted)**, `appliedConditions[]`는 추출 가능 시만"을 요구한다. `normalize_price`는 `카할`(카드할인)·`유료배송(금액미상)`·`N카드`·펨코의 `조건부무료배송:와우무배` 등을 계산하고, `to_raw_records`가 `raw_deal_post.raw._derived.applied_conditions`에 보존한다(2026-07-09). 여기까지는 옳고, 테스트도 잠가 뒀다.
- **🔴 그런데 거기서 끝난다(2026-07-10 실측)**: `deal_event.applied_conditions text[]` 컬럼은 **V1에 이미 있다.** 그러나 ① `DealEvent` 도메인 record에 그 필드가 **없고** ② `DealEventEntity`가 "applied_conditions·confidence는 미매핑"이라 스스로 적어 뒀으며 ③ `IngestDealsUseCase.candidateFrom`은 `post.getHeadlinePrice()`만 읽는다. **태그는 `raw` jsonb에 갇혀 아무도 읽지 않는다.**
- **왜 위험한가 (골든 전수 실측)**: 뽐뿌 21딜 중 **2건**(9.5%), 펨코 20딜 중 **3건**(15%)이 조건 태그를 가진다 — 즉 **표본의 약 1할이 조건부 가격인데 무조건 가격으로 기준가에 들어간다.**
  - `카할` 예: 1,800,000원 딜은 **특정 카드 보유자만** 그 가격이다. 알림이 달성 불가능한 가격을 제시한다.
  - `유료배송(금액미상)` 예: 16,450원 딜은 배송비를 몰라 **0을 더했다.** BM-02의 "실결제가 + 배송비" 자체를 못 지킨 값이고, 표본이 **실제보다 낮게** 편향된다 → 기준가가 낮아져 진짜 좋은 딜이 "괜찮은 딜" 판정을 못 받는다(놓침, 절대 원칙 3).
  - 화면(`cases[]`)도 알림 본문도 조건을 한 글자도 말하지 않는다. **절대 원칙 1(정직성) 위반이고, 아무 로그도 남지 않는다.**
- **잠정값**: collector는 계속 `_derived`에 보존한다(생산자 쪽은 옳다). `cycle` 이벤트에 `conditional=N`(0도 센다).
- **⚠️ 이전 재개 트리거가 틀렸다**: "`raw_deal_post`에 `applied_conditions` 컬럼 추가"라고 적혀 있었으나, **컬럼을 더해도 `DealEvent`에 필드가 없어 값은 여전히 도달하지 않는다.** 구현 수단을 적어 두면 다음 세션이 그걸 요구사항으로 읽는다(Q-50·Q-48·Q-34에 이어 네 번째).
- **✅ 절반 해소(2026-07-10)**: "**core 기존 파일이라 조율**"도 **틀렸다**(같은 병의 다섯 번째). 딜은 이미 있고 `deal_event_source`가 원문을 가리키므로, 상대 파일을 한 줄도 고치지 않고 **신규 파일 + 네이티브 SQL**로 태그를 끌어올렸다 — `PreserveAppliedConditionsUseCase`(멱등. 정렬을 `collate "C"`로 고정한다: 기본 정렬은 서버 로케일이 정해서 로케일이 바뀌면 매 틱 UPDATE가 돈다). `PipelineScheduler`가 **ingest 바로 뒤**에 부른다(방금 링크된 원문의 태그가 같은 틱에 보존된다). **소비자를 함께 넣었다** — `PipelineTickReport.conditionalTotal`(표본 안 조건부 딜의 절대 수). 안 그러면 "쓰기만 하는 컬럼"을 새로 만드는 셈이다. 스모크 5-1g가 종단 증명(`raw._derived` → `deal_event.applied_conditions`, `base_price`는 NULL = **역산 없음**).
- **정정**: "표본의 1할이 오염된다"는 이전 서술은 **과했다.** 확정본 AC-2는 "890,000이 **그대로 분포 입력**이 되고 applied_conditions에 태그만 남는다"고 한다 — 분포는 as-posted가 옳다. 결함은 오염이 아니라 **표시 누락**이었다.
- **🔴 남은 절반(여전히 열림)**: ① **화면·알림이 태그를 말하지 않는다.** `BenchmarkView`·`GetBenchmarkUseCase`(core 기존 파일)가 `cases[]`에 태그를 실어야 web이 "이 가격은 N카드 조건부"라고 쓸 수 있다. 지금 태그를 보는 유일한 창은 파이프라인 로그다. ② **`유료배송(금액미상)`은 별개 결함이다** — 배송비를 몰라 0을 더했으므로 BM-02의 "실결제가 + 배송비"를 못 지킨 값이고, 표본이 실제보다 **낮게** 편향된다(기준가가 낮아져 진짜 좋은 딜을 놓친다, 절대 원칙 3). 태그 보존으로 **보이게** 됐을 뿐 고쳐지지 않았다.
- **②의 절반도 진행(2026-07-10)**: 배송비 미상 딜에 **안정된 표식 `배송비미상`**을 달았다(`pipeline/price.py: SHIPPING_UNKNOWN`). 뽐뿌 `유배`뿐 아니라 펨코의 `조건부무료배송:*`(와우무배·네멤무료·`1만5천원무료`)도 같은 부류다 — 멤버십·장바구니 임계라 딜 한 건으로는 충족 여부를 알 수 없다. golden 전수 **69딜 중 4건(5.8%)**, 가격은 한 건도 바꾸지 않았다(값을 지어내지 않는다). `conditional`과 별도로 `shipping_unknown` 카운터를 낸다. 태그는 `PreserveAppliedConditionsUseCase`를 타고 `deal_event.applied_conditions`까지 간다 → **core는 이제 산문 매칭이 아니라 `'배송비미상' = any(applied_conditions)`로 걸러낼 수 있다.**
- **재개 트리거**(무엇이 참이 되어야 하는가): ① 기준가 응답과 알림 본문이 딜의 조건 태그를 **읽고 말한다** ② **기준가 표본이 `배송비미상` 딜을 하한으로 취급한다**(분포에서 빼거나, 최소한 그 딜이 기준가를 끌어내리지 못하게 한다). 둘 다 `BenchmarkCalculator`·`GetBenchmarkUseCase`(core 기존 파일)라 상대와 조율. **실 폴링 전 필수는 ②** — 지금 켜면 표본의 약 6%가 실제보다 낮은 값으로 기준가를 끈다. 다만 이제 그 비율이 `docker logs`의 `shipping_unknown=N`으로 **보인다.**

## [열림] Q-45. 드리프트 임계 수치(window·min_success_rate·zero_yield_streak) — 미승인 잠정
- **맥락**: REL-06 드리프트 감지(Q-40 해소)의 임계값이 `docs/31` 승인 수치에 없다. 실 수집 데이터 없이 정한 값이다.
- **잠정값**: `window=10, min_success_rate=0.6, zero_yield_streak=3`. `DriftPolicy`로 **주입**(Q-14·Q-37 선례 — 승인값과 미승인값을 섞지 않는다). `docs/31` 위임 수치 표 등재.
- **재개 트리거**: 실 수집 1~2주 관측 후 — 오탐(새벽 시간대 0건, 일시 5xx 다발)이 잦으면 임계 상향, 구조 변경을 놓치면 하향. 특히 `zero_yield_streak=3`은 게시판이 3주기(3분) 연속 새 글 0건일 수 있는지에 달렸다.

## [열림] Q-42. SEC-01 gitleaks — CI 게이트 구현 완료, **로컬 pre-commit 훅은 미활성**
- **맥락**: SEC-01은 "커밋 전 훅으로 스캔(gitleaks)"을 명시한다. 보류 사유였던 "로컬 바이너리 설치"는 **CI에선 필요 없다**(컨테이너로 실행).
- **구현(2026-07-09)**: CI에 `secrets` 잡 — `fetch-depth: 0`으로 **히스토리 전체**(58커밋) 스캔. `.gitleaks.toml`은 기본 규칙셋 + **좁은 예외 하나**: `collector/tests/fixtures/**.{html,json}`. 캡처한 남의 페이지에 그 사이트의 토큰이 박혀 있어 오탐이 난다(펨코 `list_normal.html:65`). **디렉토리 전체를 예외로 두지 않았다** — 같은 토큰을 `.txt`로 옮기면 잡히는 것을 실측 확인했다. 스캔 결과 **우리 시크릿 유출은 0건**.
- **예외 갱신(2026-07-09)**: `curl-auth-user` 오탐 1건 추가(`scripts/smoke.sh`의 Basic Auth 리허설 자격증명). gitleaks 8.30은 구형 `[allowlist]`와 신형 `[[allowlists]]` 혼용을 거부하므로 둘 다 신형으로 옮겼고, `condition = "AND"`(규칙·경로·문자열 모두 일치)로 좁혔다. **히스토리를 스캔하는 게이트는 HEAD 수정으로 초록이 되지 않는다** — 도입 커밋이 불변이라 예외가 유일한 길이었다.
- **훅이 실제로 도는 것을 확인(2026-07-09)**: `.githooks/pre-commit.test.sh`가 일회용 저장소에서 4갈래를 시험한다 — 진짜 자격증명 차단 / 승인된 리터럴 통과 / 평범한 변경 통과 / 같은 문자열이라도 다른 파일이면 차단. CI `secrets` 잡에 포함. **훅은 만들기 쉽고, 켜지 않으면 도는지 아무도 모른다.**
- **잔여**: `.githooks/pre-commit`을 **활성화하지 않았다**(`git config core.hooksPath .githooks` — 머신 로컬 설정이라 각자 켠다). 매 커밋마다 컨테이너를 띄운다(수 초). Docker가 없으면 조용히 넘어가지 않고 **실패**한다 — "스캔했다고 착각"이 가장 나쁘다. 참고: 이번 `curl-auth-user` 오탐은 훅이 켜져 있었다면 **커밋 시점에** 걸렸을 것이고, CI가 뒤늦게 빨개지지 않았을 것이다.
- **재개 트리거**: 커밋 지연이 감당되면 훅 활성화. 오탐이 늘면 `.gitleaks.toml` allowlist를 **좁게** 확장(디렉토리 전체 금지).

## [열림] Q-43. code intelligence(LSP) 플러그인 미도입
- **맥락**: Claude Code 문서의 도입 트리거 표에 "심볼 정의를 찾으려 파일을 많이 읽는다 → code intelligence 플러그인"이 있다. 2026-07-09 세션에서 Explore 에이전트가 core를 훑다가 REST 계약을 지어낸 상황이 정확히 여기 해당한다.
- **잠정값**: 미도입. Grep/Read로 진행하고, 로드베어링 주장은 원문 대조로 검증(자동 메모리 `verify-subagent-claims`).
- **재개 트리거**: Java·Python 플러그인 마켓플레이스 의존 추가를 승인할 때. 외부 의존이 늘어나므로 사용자 결정.


## [열림] Q-63. 가격 후보 서열에서 `만원` 축약이 조건 문구를 가격으로 읽는다
- **맥락**: `_extract_main_price`의 서열은 `만원 축약 → 원 붙은 숫자 → 콤마 숫자 → 맨 4자리`다. 1순위라 제목에 섞인 **조건 문구**가 실제 가격을 이긴다.
  - `선풍기 35,000원 5만원 이상 무료배송` → **50,000원**(실제 35,000).
  - 뒤집으면(원 붙은 숫자 먼저) 다른 게 깨진다: `사은품 1,000원 상당 / 본품 12만원` → **1,000원**(실제 120,000).
- **잠정값**: 서열 유지(문서화된 의도). 2026-07-10에 **단위 가드**만 넣었다 — `만` 뒤가 글자·숫자면 가격이 아니다(`5000만화소`·`3만시간`·`2만mAh`). golden 69딜 전수 대조: 가격·태그 변화 0건.
- **왜 지금 못 정하나**: 어느 오검출이 실제로 더 흔한지 **실 제목 표본이 없다.** golden 69딜에는 둘 다 0건이다(루리웹 22딜이 괄호 없이 오지만 조건 문구가 없다). 합성 케이스로는 빈도를 알 수 없고, 빈도 없이 서열을 바꾸면 한쪽 오검출을 다른 쪽 오검출로 바꾸는 것이다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 수집(또는 fixture 추가 채취)으로 **제목에 `N만원` 문구와 별도 가격이 함께 오는 빈도**와 **가격이 오직 `N만원`으로만 표기되는 빈도**를 각각 셀 수 있게 되면, 흔한 쪽을 이기게 서열을 정한다. 그때 `docs/99`의 "before/after 전수 대조" 규칙대로 증명한다.
- **위험도**: 오검출 시 그 딜 하나가 분포에 들어간다. `n ≥ 5`면 BM-05 이상치 판정(Tukey)이 상단/하단을 잡아 리뷰 큐로 보낸다 — **완충 장치는 있으나 표본이 적으면 안 돈다.**

## [열림] Q-64. 제목에 배송 표기가 아예 없는 딜의 배송비를 0으로 더하고 있다
- **맥락**: 배송 표기가 있는 딜은 이제 전부 해석된다(`classify_shipping`: 무료 / 금액 / 조건부 / 미지 어휘 / 픽업). 그런데 **표기 자체가 없는 딜**이 많다 — golden 실측 **루리웹 26/28 · 뽐뿌 2/21**. 이들에겐 배송비 0을 더한다.
- **왜 위험한가**: BM-02의 저장 기준은 "실결제가 + 배송비"다. 실제 배송비가 3,000원이면 표본이 그만큼 **낮게** 편향되고 기준가가 내려가 진짜 좋은 딜을 놓친다(절대 원칙 3). 루리웹 표본의 대부분이 여기 해당한다.
- **잠정값**: 0을 더한다(현행). **`배송비미상` 표식을 달지 않았다** — 달면 루리웹 표본의 93%가 "하한"이 되어 표식이 신호를 잃는다(`shipping_unknown` 카운터가 무의미해진다). 즉 지금은 "모른다"를 조용히 0으로 두고 있고, **그 사실을 이 항목이 기록한다.**
- **판단이 갈리는 지점**: ① 표기 없음 = 무료가 관례인가(핫딜 게시판에서 무료배송은 굳이 안 적는다는 가설) ② 아니면 사이트별로 다른가(뽐뿌는 2/21만 무표기 → 표기가 규범, 루리웹은 26/28 → 무표기가 규범). **가설을 검증한 적이 없다.**
- **재개 트리거**(무엇이 참이 되어야 하는가): 사이트별로 "표기 없는 딜의 실제 배송비"를 **원문 본문**(리스트가 아니라 글 내용)에서 표본 확인할 수 있게 되면 — 무표기가 무료를 뜻하는지 사이트마다 판정하고, 아니면 본문 파싱으로 배송비를 얻는다. 그때까지는 **사이트 간 기준가를 섞을 때 이 편향이 사이트마다 다르다는 사실**을 잊지 않는다(뽐뿌는 표기가 규범이라 편향이 작다).
- **관련**: Q-46(조건 태그의 화면 표시), Q-63(가격 후보 서열).
