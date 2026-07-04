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

## [열림] Q-2. 전역 API 컨벤션(응답 봉투·에러 형식) 미확정
- **맥락**: core REST 표면이 아직 작아(기준가 조회 1본) 전역 컨벤션 문서를 만들지 않았다. `docs/benchmark/03`·`07`은 봉투 없는 리소스 직접 반환 + `{code, message}` 에러를 "제안(미적용)"으로 시드해 둔 상태.
- **잠정값**: 리소스 직접 반환, 에러 `{code, message}`. seam = core web adapter의 응답 매핑 1곳(@ControllerAdvice 등가).
- **재개 트리거**: M1 web 최소 슬라이스(등록+설정) 착수 시 엔드포인트가 늘어나는 시점 — 확정 후 benchmark/03·07의 "제안(미적용)" 표기 제거.

## [열림] Q-3. 네이버 쇼핑 API 스파이크·연동 보류 (키 미발급)
- **맥락**: M0-4 스파이크 중 "아이폰 17 256" 응답 품질 확인은 네이버 개발자센터 앱 등록(Client ID/Secret)이 필요한데 미발급 상태(사용자 확인). 현재가(BM-06 currentPrice)·기능1 등록 후보 조회의 데이터 소스.
- **잠정값**: 네이버 어댑터는 port 인터페이스만 두고 미구현. 기준가 계산·테스트는 현재가를 주입값으로 대체(도메인은 이미 현재가를 입력으로 받음).
- **재개 트리거**: 키 발급 → 루트 `.env`에 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET` 주입 → 스파이크 실행(응답 품질·정확 매칭 가능성) → 결과 `98-field-notes` → 어댑터 구현.

## [열림] Q-4. used(중고) 스키마는 V2로 이월
- **맥락**: M0-3 Flyway V1은 신품 코어 루프(M1=REG+BM+AL)만 담았다. 중고(기능5)의 UsedSearch/Listing/EAV 메모·축 테이블(`docs/02-domain-model.md`)은 M2 범위라 V1에서 제외.
- **잠정값**: V1에 미포함. used 도메인 코드·테이블은 M2 착수 시 `V2__used.sql`로 추가.
- **재개 트리거**: M2(중고) 착수 → used 모듈 문서 세트(`docs/used/`) 작성 → V2 마이그레이션 TDD.

## [열림] Q-5. 뽐뿌 golden fixture 재채취 필요
- **맥락**: M0-4 스파이크에서 뽐뿌가 커스텀 UA에 정상 리스트 마크업을 주지 않아(`docs/98` 뽐뿌 항목) 현재 `tests/fixtures/ppomppu/list_normal.html`이 골든으로 부적합.
- **잠정값**: 루리웹·펨코·번개장터 fixture로 먼저 파서 TDD 진행. 뽐뿌 파서는 재채취 후.
- **재개 트리거**: M1 collector 파서 착수 시 — 실제 브라우저(개인용·차단 없는 공개 페이지)로 뽐뿌 리스트 재채취 → fixture 교체 → 오픈소스 셀렉터(`revolution_main_table`) 대조.

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

## [열림] Q-18. BM-02 가격 파싱은 규칙 기반 휴리스틱 (오검출 여지)
- **맥락**: AC 경계 "원 없는 숫자"를 지원하려 4자리 이상 숫자를 가격 후보로 본다. 연도(2026)·모델번호 등이 오검출될 수 있다.
- **잠정값**: `collector/pipeline/price.py`의 정규식(만원 축약 우선 → 콤마/4자리 숫자). 배송비/무료배송 합산, 카드 태그 보존, 미검출 시 None(스킵). seam = price.py 정규식 1곳.
- **재개 트리거**: 1차 검증에서 오검출(비가격 숫자를 가격으로) 관측 시 — 가격 문맥(원·가격 키워드 근접) 요구·후보 스코어링 도입.
