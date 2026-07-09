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

## [열림] Q-15. BM-05 🔥 최우선 알림·리뷰 큐 영속화는 AL/어댑터 관심사
- **맥락**: AC-2 "🔥 최우선 알림", AC-3 승격/기각 UI는 알림·큐 처리 영역. BM-05(순수)는 outlierFlag 판정 + `ReviewQueueItem` 값 생성 + DealEvent 전이(promote/reject)까지만.
- **잠정값**: BM-05는 `OutlierDetector.classify/reviewItemFor` + `DealEvent.promoteFromOutlier/reject`만 보장. 큐 영속화·🔥 우선순위 발화는 AL 모듈이 OUTLIER_LOWER 타입으로 처리.
- **재개 트리거**: AL(기능3) 착수 시 reviewQueue 처리·알림 우선순위 배선.

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
- **✅ 상태→ENDED 절반 해소(2026-07-09)**: 신규 `ReprocessDealStatusUseCase.reprocessEndedDeals()` — 링크된 **모든** 원문이 SOLD_OUT/DELETED면 `deal_event`를 ENDED로, last_seen을 종료 근거 시각으로 단조 갱신. `findUnprocessed`·`IngestDealsUseCase` **무수정**(additive: `DealEventEntity.applyStatusChange`·`DealEventRepository.findByStatusIn`만). Testcontainers 4케이스(단일 품절·DELETED·다중소스 중 하나 ACTIVE 유지·전부 ACTIVE 무변). **잔여(여전히 열림)**: ① 가격변화 재처리(raw `headline_price` → deal `priceLast`), ② `captured_at>last_seen` 변경 감지기(전수 스캔이라 정확성은 무관, 효율 seam), ③ 최초 수집 시 이미 품절인 원문(ingest가 ACTIVE로 생성 — ingest 관심사), ④ 애매/스킵 글 재처리 중복(원 Q-27), ⑤ 배치 오케스트레이션(스케줄러가 `ingestPending`+`reprocessEndedDeals` 동반 호출 — 프로덕션 스케줄러 부재).

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

---

_(이하 2026-07-09 collector 스케줄러(폴링 루프·백오프·차단 중지) 착수에서 등장.)_

## [열림] Q-37. 지수 백오프 수치(base·factor·cap) — 미승인 잠정 파라미터
- **맥락**: `docs/11` line 4가 "지수 백오프"를 요구하나 수치는 어디에도 없다(docs/31 승인 6값에 없음). 폴링 실패 재시도 간격을 결정.
- **잠정값**: `base=60s, factor=2, cap=30min`. `BackoffPolicy`(frozen dataclass)로 **주입** — 순수 함수는 주입값만 참조하고 모듈 상수에 하드코딩하지 않는다(Q-14 `absurdityRatio` 선례와 동일: 승인값과 미승인값을 섞지 않음). 지터 없음(1인용·사이트당 1커넥션이라 thundering herd 무관, 테스트 결정성 우선).
- **재개 트리거**: 운영자 승인 요청(docs/31 위임 수치 표에 등재됨) → 승인 시 상단 표 편입. 실 수집에서 5xx 빈발·복구 지연 관측 시 폭 재조정.

_(Q-38. robots.txt 준수 게이트 — **해소됨 2026-07-09**: `scheduler/fetcher.py`의 `RobotsGate`로 구현. `urllib.robotparser`(stdlib) — 신규 의존 0. 호스트별 1회 조회 후 캐시, 404면 전체 허용(표준). **Disallow를 `403`으로 매핑**해 `classify_status`가 BLOCKED로 보게 했다 → 사이트 자동 중지 + Alert, 재시도 없음(접근 금지를 일시 장애로 격하하지 않는다). **Crawl-delay가 우리 하한보다 길면 따르고 짧으면 우리 하한이 이긴다**(SEC-08 "설정으로 완화 불가"). ⚠️ **실 robots.txt와 대조하지 않았다** — fake opener 테스트만. 실 수집 착수 시 pre-deploy §F에서 확인. 여기서 제거.)_

_(Q-41. `parse_bunjang`의 `status="ENDED"` 불일치 — **해소됨 2026-07-09**: `SOLD_OUT`으로 수정. 종단 스모크가 없어 3일간 잠복했던 계약 위반(`to_raw_records`가 `ValueError`). ⚠️ **잔류**: 번개 status 코드표는 여전히 미실측(`"0"=판매중`만 안다). 비-`"0"`을 전부 SOLD_OUT으로 보는 건 잠정이며 `예약중`을 판매완료로 오독할 수 있다 → 아래 Q-44로 좁혀 유지. 여기서 제거.)_

## [열림] Q-44. 번개장터 status 코드표 미실측
- **맥락**: `docs/98`은 `status`("0"=판매중 등)만 기록한다. 나머지 코드(예약중·판매완료·삭제)가 각각 무엇인지 모른다.
- **잠정값**: `parse_bunjang`은 `"0"→ACTIVE`, **비-"0" 전부 →`SOLD_OUT`**. 예약중 매물을 판매완료로 오독할 수 있다(중고 생애주기 알림이 조기 발화). 파서 docstring에 명시.
- **재개 트리거**: M2(중고) 착수 시 — 번개 검색 응답을 여러 상태로 실측해 코드표를 `docs/98`에 기록하고, `예약중`을 ACTIVE로 되돌릴지 결정. `used_listing_observation` 생애주기 판정(docs/14)과 함께.

_(Q-39. BLOCKED 자동 중지의 수동 재개 경로 — **`working-area/decisions-needed.md` D-3으로 승격 2026-07-09**: SEC-08 "재시도 강행 금지"의 해석이 걸려 잠정값으로 진행할 수 없다(정책 결정). **Q-36(커서 영속화) 착수 전 필수 선결.** 여기서 제거.)_

_(Q-40. REL-06 파싱 드리프트 감지 — **해소됨 2026-07-09**: `scheduler/drift.py`(순수, 이동창). 두 신호를 본다 — ① **조용한 0건**(성공인데 연속 0건 = 구조 변경의 전형. 뽐뿌 셀렉터 체인이 끊겼을 때 예외 없이 0건이었다) ② **성공률 저하**(창 안 TRANSIENT 비율). BLOCKED는 세지 않는다(이미 중지+Alert). 창 미충족 시 미판정, 회복 시 재무장, 같은 증상 반복 알림 억제. `__main__`이 사이클마다 관측을 먹인다. 임계는 **미승인 잠정 주입**(아래 Q-45). 여기서 제거.)_

_(Q-47. web 등록 폼 가격축 조합 — **해소됨 2026-07-09**: `buildCommand`가 데카르트 곱을 만든다(용량 2 × 색상 2 → variant 4). 축 이름 중복은 거부(맵에서 덮어쓰기), 빈 축 행은 무시, 화면이 "생성될 variant N개"를 미리 보여준다. 여기서 제거.)_

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

## [열림] Q-50. OBS-04 — core에 전용 헬스 엔드포인트가 없다 (compose healthcheck는 임시방편으로 붙임)
- **맥락**: OBS-04는 "헬스체크 엔드포인트(컴포넌트별) + Compose healthcheck"를 요구한다. **compose healthcheck는 붙였다**(2026-07-09): postgres(`pg_isready`) · core(`curl /api/v1/products`) · web(`wget /healthz`, `auth_basic off`). collector는 **일부러 걸지 않았다** — 1회 실행 후 종료하는 배치라 살아 있음을 물으면 항상 죽어 있다(관측은 OBS-01 로그 이벤트로).
- **남은 문제**: core에 `spring-boot-starter-actuator`가 없어 **비즈니스 엔드포인트를 헬스체크로 쓴다**. `/api/v1/products`는 DB까지 닿으므로 readiness엔 가깝지만, ① 목록이 커지면 헬스체크가 비싸지고 ② "core는 살아 있는데 DB만 죽음"을 구분하지 못하며 ③ 엔드포인트 경로가 바뀌면 헬스체크가 조용히 깨진다.
- **잠정값**: 위 임시방편으로 진행. `start_period: 40s`(Flyway 마이그레이션 시간), `interval: 10s`. web 헬스체크는 nginx만 증명하고 core를 증명하지 않는다 — web은 core 없이도 떠야 하므로 의도된 것이다.
- **재개 트리거**: `core/build.gradle.kts`에 actuator 추가 + `/actuator/health` 노출 → compose의 core healthcheck 교체(1줄). **core 기존 파일이라 상대와 조율.**

## [열림] Q-49. `POST /api/v1/products`에 서버측 검증이 없다 — 잘못된 입력이 500이 된다
- **맥락**: `RegistrationController`는 `@Valid`를 쓰지 않고 `RegisterProductCommand`에도 컴팩트 생성자 검증이 없다(`spring-boot-starter-validation`은 의존성에 있으나 미사용). 빈 `name`으로 POST하면 `ProductEntity.name nullable=false` 제약에 걸려 **`DataIntegrityViolationException` → 500**이 난다. `axes`/`variants`가 `null`이면 `NullPointerException` → 500.
- **왜 발견됐나**: web 최소 슬라이스가 `docs/benchmark/07`의 "FE code별 분기 확정 기재" 의무를 이행하며 드러났다. 클라이언트(`buildCommand`)가 먼저 검증하므로 화면으로는 안 보이지만, **curl로 직접 치면 500**이다. 클라이언트 검증은 방어가 아니라 편의다.
- **잠정값**: 미수정. web이 클라이언트 검증으로 가린다. `ApiExceptionHandler`는 이 예외들을 매핑하지 않으므로 `{code,message}`도 못 준다 → 클라이언트는 `HTTP_500`으로 처리.
- **재개 트리거**: `RegistrationController`/`RegisterProductCommand`는 **기존 core 파일**이라 상대와 조율 대상. 착수 시 — `@Valid` + `@NotBlank`/`@NotEmpty` 또는 record 컴팩트 생성자 검증, `MethodArgumentNotValidException`·`IllegalArgumentException`을 `ApiExceptionHandler`에 400으로 매핑(신규 code 필요, 예: `REG_INVALID_COMMAND`).

## [열림] Q-48. 알림 정책 설정(REG-03) 화면을 만들 REST가 없다
- **맥락**: 확정본 §7의 web 최소 슬라이스는 "등록 + 후보선택 + **variant/키워드/목표가 설정**"이다. `alert_policy` 테이블·`AlertPolicyEntity`·`AlertPolicyRepository`는 있으나 **컨트롤러가 없다** — 목표가·기간 P·K_display·제외 키워드·quiet hours를 화면에서 저장할 수 없다.
- **잠정값**: web은 등록+목록만. 설정은 미구현. 사용자 승인은 "**읽기 전용** 조회 API 추가"까지였고 정책 저장은 쓰기 API라 범위 밖.
- **재개 트리거**: `AlertPolicyController`(GET/PUT) 착수 시 — core 소유 영역이라 상대와 조율. 그때 REG-03 화면.

## [열림] Q-46. 조건 태그(`applied_conditions`)를 담을 컬럼이 `raw_deal_post`에 없다
- **맥락**: BM-02 AC-2는 "카드·쿠폰 조건가는 as-posted(역산 금지), **태그만 보존**"을 요구한다. `normalize_price`는 `카할`(카드할인)·`유배`(유료배송 금액미상)·`N카드` 태그를 계산해왔으나 **아무도 저장하지 않아 전부 버려지고 있었다**(2026-07-09 발견). 펨코의 `와우무배`·`네멤무료`·`1만5천원무료`(조건부 무료배송)도 마찬가지.
- **잠정값**: `ParsedDeal.applied_conditions` → `raw_deal_post.raw` jsonb의 **`_derived.applied_conditions`** 키에 저장. `raw`는 docs/01상 "크롤링 원본 보관 전용"이라 파생 데이터임이 드러나도록 `_derived` 아래에 분리했다. **가장 보수적 선택** — 스키마 변경 0, core 무영향(`RawDealPost` 엔티티는 `raw`를 매핑조차 하지 않는다).
- **왜 중요한가**: 태그가 없으면 "누구나 이 가격"인지 "쿠팡 와우 회원만 무료배송"인지 구분할 수 없다. 기준가 표본에 조건가가 섞이고, 알림이 달성 불가능한 가격을 제시한다(절대 원칙 1 정직성).
- **재개 트리거**: 소비처(기준가 표시·알림 본문·와우가/일반가 손잡이 docs/90 §8)가 조건을 실제로 읽을 때 — `raw_deal_post`에 `applied_conditions text[]` 컬럼 추가를 core와 조율(Flyway는 core 단독 소유). 그때 `_derived` 임시 경로를 제거.

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

