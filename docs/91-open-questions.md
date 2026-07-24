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

## [열림] Q-3. 네이버 쇼핑 API — **2026-07-31 서비스 종료 공지, 키 발급으로는 해결 안 됨**
- **맥락**: M0-4 스파이크 중 "아이폰 17 256" 응답 품질 확인은 네이버 개발자센터 앱 등록(Client ID/Secret)이 필요한데 미발급 상태였다. 현재가(BM-06 currentPrice)·기능1 등록 후보 조회(REG-01)·CMP-01 네이버 leg의 데이터 소스.
- **🔴 2026-07-24 확인(사용자가 공지 원문 제공)**: 네이버 개발자센터가 검색 **쇼핑/책/전문자료** API를 **2026-07-31(금)**부로 전면 종료한다고 공지했다(`dl_naver_search_api@navercorp.com` 문의처, 대체 안내 없음). **키를 지금 발급받아도 일주일 뒤면 죽는다** — "재개 트리거 = 키 발급"이라는 이전 잠정값이 통째로 무의미해졌다. 이건 기술 보류가 아니라 **데이터 소스 자체가 사라지는 것**이라 잠정값으로 밀고 갈 사안이 아니다.
- **영향 범위**: BM-06 `currentPrice`(갭 계산·콜드스타트 잭팟 트리거의 유일한 입력) · REG-01 후보 검색(현재 수동 폴백 문구로 이미 우회 중, 확정본에 "실 fetch는 정지조건" 원칙과 정합) · CMP-01 네이버 leg(Q-79).
- **잠정값(변경 없음, 그대로 유효)**: `CurrentPriceProvider`는 port 인터페이스만 두고 스텁(`StubCurrentPriceProvider`, 항상 null) — 이 설계 덕분에 "네이버가 없다"는 사실이 도메인·계산에 이미 안전하게 흡수돼 있다(Q-53 "현재가 미확립"이 정직하게 null로 드러난다). 코드 변경은 필요 없다.
- **재개 트리거를 새로 정하지 않는다 — 사람이 정할 사안으로 승격**: 대안(스크래핑 vs 다른 공개 API vs 쿠팡 확장만으로 대체 vs 현재가 없이 운영)마다 절대 원칙 5(공식 API 우선·차단 우회 금지·차단 없는 공개 페이지는 저빈도 수용)의 해석이 갈린다 — `working-area/decisions-needed.md` D-7로 옮김.
- **관련**: Q-79(CMP-01 네이버 leg), D-7(신규).

## [열림] Q-4. used(중고) 스키마는 후속 마이그레이션으로 이월
- **맥락**: M0-3 Flyway V1은 신품 코어 루프(M1=REG+BM+AL)만 담았다. 중고(기능5)의 UsedSearch/Listing/EAV 메모·축 테이블(`docs/02-domain-model.md`)은 M2 범위라 V1에서 제외.
- **잠정값**: V1에 미포함. used 도메인 코드·테이블은 M2 착수 시 마이그레이션으로 추가.
- **⚠️ 번호 정정(2026-07-11)**: 원래 `V2__used.sql`로 예약했으나, **V2 슬롯은 `V2__purchase.sql`(PUR)이 소진했다**(PUR이 M5→M1로 앞당겨지며 V2 번호를 가져감). used 스키마는 **V3+**로 작성한다. `docs/used/02-data-model.md`가 방향을 잡았고, 실제 마이그레이션은 core 단독 소유(Flyway)라 상대 개발자가 TDD로 확정한다.
- **재개 트리거**: M2(중고) 착수 → used 모듈 문서 세트(`docs/used/`) 작성 ✅(2026-07-11 초안) → core V3 마이그레이션 TDD.

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

## [해소 2026-07-23] Q-11. includeOutliers 토글은 계산 진실 밖
- **맥락**: `docs/benchmark/03` line 9의 `includeOutliers`(기본 false)는 표시 손잡이 — "계산 진실은 불변". 순수 계산기는 항상 이상치를 제외한다.
- **✅ 이 항목의 재개 트리거는 이미 참이었다**: "M1 web 슬라이스에서 기준가 화면 구현 시"라고 적혀 있었는데, `DecisionPage`(기준가 화면)는 오래전부터 서 있었다 — Q-46·Q-48·Q-67과 같은 모양의 **거짓 봉인**이었다. 실측(2026-07-23): `GetBenchmarkUseCase.getBenchmark(...)`는 `includeOutliers` 파라미터를 **받기만 하고 계산기로 한 번도 넘기지 않았다**(호출자 0과 같은 결의 결함 — "파라미터 0").
- **해소 내용**: `BenchmarkCalculator.compute(...)`에 `includeOutliers` 인자 추가 + `BenchmarkView.outliers` 필드 신설. 손잡이가 켜져도 tier·n·benchmarkPrice 등 계산 진실은 **완전히 불변**(같은 window의 `candidates`에서 outlierFlag≠NONE만 걸러 별도 목록으로 조립, pricingSample과 무관). `GetBenchmarkUseCase`가 이제 실제로 넘긴다. REST(`BenchmarkController`)는 이미 파라미터를 받고 있었으므로 무변경. web `DecisionPage`에 "이상치 포함(참고용)" 체크박스 추가 — 기본 꺼짐, 켜면 계산과 분리된 목록을 원문 링크와 함께 보여준다.
- **검증**: 배선 뮤테이션으로 RED 확인(`GetBenchmarkUseCaseTest`), REST 계약 테스트(`BenchmarkControllerTest`), 실 브라우저로 실 데이터에 대고 토글 on/off 확인(n=6 불변, 이상치 1건만 별도 표시).

## [열림] Q-12. 병합 가격 허용폭의 기준가(base) = anchor 딜
- **맥락**: `MERGE_PRICE_TOLERANCE` = max(base×ratio, floor)에서 base를 두 딜 중 무엇으로 삼을지 docs 미명시(BM-04).
- **잠정값**: base = **기존(anchor·먼저 처리된) 딜의 priceFirst**. seam = `DealMergePolicy.priceWithinTolerance` 1곳. 경계 포함(≤). 시간 윈도도 경계 포함(48h 정확=병합).
- **재개 트리거**: 1차 검증에서 과분할(같은 딜 갈라짐)/오병합 관측 시 base 정의(min/max/평균) 재조정.

_(Q-13. BM-04 병합의 알림 억제·소급 방지는 AL 모듈 관심사 — **해소됨 2026-07-23**: 배경 에이전트의 docs/91 "거짓 봉인" 스캔이 재개 트리거("AL 착수 시")가 이미 오래전 참이 됨을 지적 → 원문 대조로 실결함 확인. `IngestDealsUseCase.confirmDeal`의 병합(흡수) 분기가 신규 딜과 **똑같이** `alertEvaluation.evaluate(...)`를 호출하고 있었다 — `priceFirst`는 병합으로 안 바뀌므로 같은 트리거가 매번 다시 SEND_NOW를 냈을 결함(AC-3·AC-4 위반, 텔레그램 실전송 시 중복 문자로 드러날 것). `StubAlertSender`가 로그만 남겨 지금까지 조용했다. 수정: 병합은 `DispatchOutcome.NO_ALERT`만 반환하고 병합된 딜 id를 `IngestReport.mergedDealIds()`로 모아 `PipelineScheduler`가 이미 검증된 멱등 메커니즘 `FollowUpAlertUseCase.sendFollowUps(ids, FollowUpKind.VERIFIED)`로 넘긴다(AL-03의 세 번째 종류 VERIFIED — 선언은 됐으나 프로덕션 호출자가 0이던 것도 같이 배선). 회귀 테스트(`IngestDealsUseCaseTest#mergingASecondSiteDoesNotResendTheFirstAlert` 등)는 수정을 되돌려 RED를 확인한 뒤 복원해 뮤테이션 검증. `scripts/smoke.sh` 5-1h에 종단 단언 추가(병합 딜의 첫 알림이 정확히 1번, VERIFIED 후속이 스텁 로그·틱 카운터에 남음) — 격리 컨테이너로 실행해 통과 확인. decision-log 참조. 여기서 제거.)_

## [열림] Q-14. SPARSE 폴백 컷 밴드폭(absurdityRatio) — 미승인 잠정 파라미터
- **맥락**: BM-05 AC-5 SPARSE 구간 폴백 컷은 "현재가 대비 비상식 가격"을 걸러야 하나, 그 밴드 폭이 docs/31 승인 6개 파라미터에 없다(신규 정책 수치).
- **잠정값**: `absurdityRatio` = 0.5(±50%). `OutlierDetector.isAbsurdVsCurrent`에 **주입**(테스트/앱 레이어). BenchmarkParams(승인 seam)엔 아직 미편입 — 승인값과 미승인값을 섞지 않기 위함.
- **재개 트리거**: 운영자 승인 요청(docs/31에 7번째 행) → 승인 시 `BenchmarkParams`로 이관 + `defaults()` 편입 + decision-log. 1차 검증에서 오염 사례 관측해 폭 재조정.

## [부분해소] Q-15. 리뷰 큐 — 읽기·쓰기·web 버튼·**텔레그램 버튼** 완료. UNCLASSIFIED 승격만 후속
- **맥락**: AC-2 "🔥 최우선 알림", AC-3 승격/기각 UI는 알림·큐 처리 영역. BM-05(순수)는 outlierFlag 판정 + `ReviewQueueItem` 값 생성 + DealEvent 전이(promote/reject)까지만.
- **읽기 해소(2026-07-10)**: `GetReviewQueueUseCase` + `ReviewQueueController`(`GET /api/v1/review-queue`) + web `미상 큐` 탭 — **전부 신규 파일**, core 기존 파일 무수정. 그전까지 `review_queue_item`은 **쓰이기만 하고 아무도 읽지 않았다**(`IngestDealsUseCase`가 넣고 `PipelineScheduler`가 세기만 함). 매칭이 무엇을 놓치는지 볼 방법이 없었다 — 놓침을 허용하는 시스템(원칙 3)에서 놓친 것을 못 보면 그건 유실이다. `status`·`created_at`을 엔티티가 매핑하지 않아 JPA 대신 **읽기 전용 SQL**로 읽는다.
- **잠정값(쓰기 없음)**: `DealEvent.promoteFromOutlier()`·`reject()`는 순수 도메인에만 있고 **프로덕션 호출자가 없다.** 화면에 승격·기각 버튼을 그리지 않는다 — 못 하는 일을 버튼으로 그리면 눌러 보고 나서야 안다(과대약속 금지). 🔥 우선순위 발화는 AL 모듈(Q-20)이 OUTLIER_LOWER로 처리.
- **재개 트리거**(무엇이 참이 되어야 하는가): ① `DealEventEntity`가 승격·기각 전이를 표현할 수 있어야 한다 ② `ReviewQueueItemEntity`가 `status`(PENDING/CONFIRMED/REJECTED)를 쓸 수 있어야 한다 ③ ~~Q-27 ④가 해소되어 같은 근거가 한 행이어야 한다~~ **✅ 충족(2026-07-12)** — 이제 `dedup_key`로 한 행에 접힌다. ①②는 core 소유(이제 우리) — 승격·기각 전이(`DealEventEntity`·`ReviewQueueItemEntity.status`)만 배선하면 착수 가능. 단 dedup가 unique-global이라 승격·기각 시 resolve-then-recur 규칙을 함께 정한다(위 Q-27 ④ "남은 것"). (구현 수단은 여럿이다 — 위 셋은 조건이지 방법이 아니다.)
- **죽은 컬럼 귀속(2026-07-11)**: `review_queue_item.channel`·`resolved_at`은 승격·기각이 채울 자리인데 쓰기가 없어 **둘 다 항상 NULL**이다(컬럼 전수 감사). 승격·기각이 배선되면 채워진다. `GetReviewQueueUseCase`가 status·created_at과 함께 이 둘도 미매핑이라 네이티브 SQL로 우회한다.
- **✅ 쓰기 해소(2026-07-12, core 소유권 조율 + Q-27④ 선결 충족)**: `ResolveReviewItemUseCase` + `POST /api/v1/review-queue/{id}/{promote|reject}`. **승격**=이상치 오탐을 정상으로(`DealEvent.promoteFromOutlier()` → outlierFlag NONE, 표본 복귀), **기각**=사기·낚시 영구 제외(`reject()` → permanentlyExcluded, BM-05 AC-3). 판단은 순수 도메인(이제 호출자 있음 — 죽은 메서드 부활), 반영은 엔티티(`setPermanentlyExcluded` 추가). status·resolved_at·**channel='WEB'**은 네이티브 SQL로 `where status='PENDING'` 원자 처리(죽은 컬럼 셋 부활). Q-27④로 같은 근거가 한 행이라 한 번 처리로 끝난다. 이미 처리/없는 항목=404(`REVIEW_ITEM_NOT_FOUND`). 테스트: `ResolveReviewItemUseCaseTest`(승격·기각·미상기각·중복처리)·`ReviewQueueEndpointTest`(HTTP 계약).
- **✅ web 버튼 해소(2026-07-12)**: `ReviewQueuePage`에 승격·기각 버튼. 이상치=둘 다, 미상=기각만(승격 버튼을 아예 안 그린다 — core의 400과 화면이 일치). 처리 후 목록 refetch로 큐에서 내려간 걸 보여주고, 실패는 `code`를 그대로 낸다(이미 처리=`REVIEW_ITEM_NOT_FOUND`). 본문 없는 200을 위해 `client.ts`에 `command()` 추가(`request()`는 빈 본문에서 터진다). 테스트: `ReviewQueuePage.test.tsx`(타입별 버튼·refetch·에러 code).
- **✅ 텔레그램 버튼 해소(2026-07-21, M1 "버튼으로 미상 분류" 기준 충족)**: 새 미상 항목 → 텔레그램 [승격][기각] 인라인 버튼 발송(`TelegramReviewNotifier`, 새 항목만 — recurrence 아님) → 누르면 `TelegramInboundPoller`(getUpdates 짧은 주기)가 받아 `ReviewCallbackRouter`로 처리(`channel='TELEGRAM'`). SEC-03 화이트리스트로 인바운드를 거른다(Q-61). 아웃바운드(버튼)·인바운드(콜백)가 짝. web과 같은 규칙: 이상치=[승격][기각], 미상=[기각]만. 실 발송/폴은 `telegram.enabled=true`(사용자 토큰) 뒤 — 기본은 스텁/no-op(실 네트워크 없음). getUpdates 실 파싱은 fake로만 검증(수동 스파이크). 테스트: `ReviewCallbackRouterTest`·`TelegramInboundPollerTest`·`TelegramReviewNotifierTest`·`IngestDealsUseCaseTest.notifiesOnceOnNewReviewItemNotOnRecurrence`.
- **잔여(여전히 열림)**: ① **UNCLASSIFIED 승격**은 사람이 variant를 골라야 해 아직 막는다(400 `REVIEW_PROMOTE_UNSUPPORTED`) — 후보 선택 입력 경로가 생기면 딜 생성으로 배선. ② resolve-then-recur: dedup_key unique-global이라 기각 후 같은 근거 재발생 시 기각 행 occurrences만 증가(재오픈 안 함) — 수용(보수적).

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
  2. ~~**무료 딜은 여전히 `None`(스킵)**~~ → **해소(2026-07-24, D-5)**: `(무료/무료)`·`GOG 무료` 등을 스킵이 아니라 가격 0 + `FREE_PRICE`(collector)/`DealTags.FREE_PRICE`(core) 태그로 낸다. 표본(core `pricingSet`)에서는 제외해 median·P25를 지키되, 발생·신호 집합·알림·표시엔 남긴다(놓침 방지, 절대 원칙 3). `scripts/check-tag-contract.sh`가 두 모듈의 표식 일치를 강제.
  3. **1~2자리 적립성 금액**(`라방 5원`, `클릭 44원`)은 의도적으로 미검출. 상품가가 아니다.
  4. ~~**펨코 구조화 경로의 배송 조건 소실**~~ → **해소(2026-07-09)**: `와우무배`·`네멤무료`·`1만5천원무료`를 `조건부무료배송:{원문}` 태그로 보존. 배송비는 as-posted 0 유지(역산 금지). 단 fixture에 유료배송(금액) 사례가 없어 그 경로는 여전히 미검증.
- **재개 트리거**: (1)(4)는 실 수집·글 본문 fetch 착수 시. (2)는 정책 결정 시. 새 오검출 패턴이 관측되면 `_UNIT` 목록과 서열을 조정(seam = `price.py` 1곳).
- **죽은 컬럼 귀속(2026-07-11)**: `raw_deal_post.body_text`가 (1)의 저장 자리다 — 잘린 제목의 가격은 **글 본문**에서 복구해야 하는데, collector는 목록 페이지만 폴링해 본문을 담지 않아 이 컬럼은 **항상 NULL**이다(컬럼 전수 감사). 글 본문 fetch가 배선되면 채워진다.

## [열림] Q-19. BM-01 파서 golden은 정상 리스트(list_normal)만 — 상태변화 케이스 fixture 미확보
- **맥락**: AC-3은 사이트×(정상/가격변경/품절/삭제) 매트릭스를 요구하나, M0 스파이크는 `list_normal`만 채취. 상태변화 fixture 없음. 뽐뿌는 fixture 자체가 부적합(Q-5).
- **잠정값**: 루리웹·펨코·번개 `list_normal` golden으로 파서 구현·검증 완료(정상 케이스). 품절 감지 로직은 존재(루리웹 제목 키워드, 펨코 `.hotdeal_var8Y`, 번개 status)하나 실 fixture 미검증. 상태변화 자체는 core JPA 슬라이스(`RawDealPostUpsertTest`)에서 검증.
- **재개 트리거**: 실 수집 착수 시 품절/가격변경/삭제된 실제 글을 fixture로 채취 → 파서 상태 감지 golden 추가.
- **진행(2026-07-09)**: 뽐뿌 파서 추가로 **4사 전부 `list_normal` golden 확보**(Q-5 해소 — 재채취 불필요). 다만 뽐뿌 `.end2` 품절 표식이 이 스냅샷엔 0건이라 SOLD_OUT 경로는 여전히 미검증. 상태변화 fixture 부재는 그대로 열려 있다.
- **🔴 실측 정정(2026-07-10)**: "품절 감지 로직은 존재하나 미검증"이라는 위 문장이 **너무 관대했다.** 루리웹의 제목 키워드 휴리스틱은 존재했지만 **한 번도 참이 될 수 없었다** — 마커 `[종료]`가 제목 앵커 밖에 있어 제목 텍스트에 들어오지 않는다. golden 28딜 중 3건이 종료인데 전부 ACTIVE로 파싱됐고, 그러면 **루리웹 딜은 영원히 ENDED가 되지 않는다.** 고쳤다(`_has_end_marker` — golden 3건이 SOLD_OUT). → `docs/99`·`docs/98`.
- **golden 커버리지 현황**: 루리웹 `[종료]` 3건 ✅ · 펨코 `.hotdeal_var8Y` 2건 ✅ · **뽐뿌 `.end2` 0건 🔴**. 뽐뿌 분기는 **합성 HTML 테스트로 잠갔다**(리팩터가 조용히 지우지 못하게). 셀렉터의 진위는 실 사이트로만 확인되고 그건 정지조건이다.
- **재개 트리거 보강**(무엇이 참이 되어야 하는가): 실 폴링 승인 후 뽐뿌 목록에서 `.end2`가 붙은 행을 **한 번이라도 관측**하면 fixture로 채취한다. 오래 관측해도 안 나오면 셀렉터가 틀린 것이므로, 루리웹처럼 **표식이 파서가 읽지 않는 자리에 있는지** 먼저 의심한다.

## [부분해소] Q-20. 텔레그램 아웃바운드 발송 어댑터 완성 — 봇 명령·인바운드·플러시·현재가만 남음
- **맥락**: AL 순수 도메인(트리거·1발·게이트·후속 자격)은 완성. 실 발송(텔레그램)·봇 명령·현재가(네이버)는 외부 연동이라 무중단 정지 조건.
- **✅ 아웃바운드 발송 해소(2026-07-21)**: `AlertMessageFormatter`(순수, AL-05 본문 — 강도 아이콘·제품/variant·가격·갭·검증·조건태그(Q-46①)·링크, SPARSE 금액 금지) + `TelegramAlertSender`(`telegram.enabled=true`일 때만, `HttpTelegramApi`로 sendMessage) + `TelegramApi` seam(fake로 검증). SEC-08 분류(2xx/5xx=일시장애/4xx=거절, 거절은 재시도 안 함, 어떤 실패에도 안 던져 틱 보호). `@ConditionalOnProperty`로 기본은 스텁(실발송 없음), opt-in만 실 어댑터 — `TelegramSenderWiringTest`가 그 스위치를 잠근다. 토큰·chat은 사용자가 `.env`에(코드는 읽기만, 토큰 값 미접촉). **실 네트워크 테스트는 금지**라 `HttpTelegramApi`는 fake로만 검증 — 실 응답은 토큰 발급 후 수동 스파이크(pre-deploy §권장).
- **✅ ② 보류분 플러시 해소(2026-07-21, v1)**: 방해금지 HOLD 알림이 더는 유실되지 않는다. `EvaluateAlertOnDeal`이 HELD면 `held_alert` 큐(V8/R8, 딜당 1건 멱등)에 적고, `FlushHeldAlertsUseCase`가 매 틱(`PipelineScheduler` 새 step) 각 보류 딜의 variant 방해금지가 **끝났으면** `evaluate()`를 **다시 부른다** — 이제 게이트가 SEND_NOW라 실제로 나간다. **저장된 본문이 아니라 재평가**(AL-07 "발송 시점 재평가"): 밤새 기준가·상태가 바뀌었으면 현재값으로 판정 → 여전히 좋으면 최신값 발송, 자격 잃었으면(기준가 하락·종료) 드롭. `@Transactional`이라 발송 부수효과·큐 삭제가 원자적(재시도 이중발송 없음). 틱 로그 `heldFlushed[sent=N dropped=M]`. 테스트: `FlushHeldAlertsUseCaseTest`(방해금지 종료→발송·아직→보류·종료딜→드롭, 시계 08:00 고정 + 정책 창을 달리해 결정적), `PipelineSchedulerTest`(플러시 결과가 리포트로 흐름), 스모크 종단. **v1 한계**: 재평가에서 종료(ENDED)된 딜은 억제돼 드롭한다 — AL-04의 "종료 시 (종료됨) 발송"은 후속 알림(①) 경로가 채운다(보류 중 끝난 딜을 굳이 알리는 실익이 낮아 v1 드롭).
- **✅ ① 인라인 버튼 콜백 해소(2026-07-21, Q-15 종단)**: 미상 큐 항목이 새로 생기면 텔레그램으로 [승격][기각] 버튼과 함께 보내고(`TelegramReviewNotifier`, 새 항목만 — dedup), 누르면 `TelegramInboundPoller`(짧은 주기 getUpdates)가 받아 `ReviewCallbackRouter`(SEC-03 화이트리스트 + 파싱)로 `ResolveReviewItem.promote/reject(id,"TELEGRAM")`을 부른다. 아웃바운드(버튼)와 인바운드(콜백)가 짝이다. 봇 명령(/status·/queue)은 후속(버튼으로 충분). ③ 현재가(네이버, Q-3). ~~④ 관리 알림~~ ✅ 해소(Q-56).
- **재개 트리거**: 실 발송은 사용자가 `.env`에 `TELEGRAM_ENABLED=true`+토큰+chat 채우면 즉시 산다(코드는 완성). 인바운드·플러시·현재가는 각기 위 ①②③.

## [열림] Q-21. AL 트리거 세부 — "역대최저 근접" 여백·SPARSE/NONE 강도 매핑
- **맥락**: AL-02의 특가 조건 "P25 이하 **또는 역대최저 근접**"에서 "근접" 여백폭 미명시. SPARSE/NONE 폴백 알림의 강도 등급도 미명시.
- **잠정값**: 특가(SPECIAL) = SUFFICIENT & price ≤ P25(goodDealLine)로만 판정(P25 ≥ 최저이므로 역대최저는 포함, "근접 여백"은 별도 미모델). SPARSE(보유 최저가 이하)·NONE(콜드스타트 30%)은 강도 **GOOD**로 매핑하되 딱지("표본 N건 참고용"/"기준 미확립 참고용")로 신뢰도 구분. seam = `AlertEvaluator`.
- **재개 트리거**: 1차 검증에서 특가 알림이 너무 좁/넓거나 SPARSE/NONE 폴백 강도 조정이 필요하면 재조정.

## [해소] Q-22. BM-07 무시→키워드 사후학습 — M1 알림 루프의 마지막 조각 (2026-07-21)
- **✅ 해소**: 죽어 있던 순수 `KeywordSuggester`(소비처 0)를 살려, 오알림을 사용자가 **되먹이는** 루프를 닫았다(M1 완료 기준 "오알림이 키워드 사후학습으로 수렴"). 딜 알림에 `[🔕무시]` 버튼(`TelegramAlertSender`, callback `ignore:{dealEventId}` — `AlertMessage`에 dealEventId 추가) → 누르면 `ReviewCallbackRouter`가 `IgnoreDealUseCase`로 넘긴다 → 딜을 노이즈로 기록(`deal_ignore` V9/R9, 딜당 1건 멱등, 제목 박제=학습 입력) → 같은 variant 무시 제목들에서 빈출 토큰(≥2건)을 `KeywordSuggester`가 뽑아 **KEYWORD_SUGGEST 큐**를 만든다. **판단은 사람**(절대 원칙 2): 자동 반영 없음 — 후보를 제안만, 사용자가 정책 패널에서 추가한다. web `reviewLine`이 KEYWORD_SUGGEST를 후보와 함께 그리고, 텔레그램은 정보성 알림(승격 아님). 임계 `MIN_FREQUENCY=2`는 잠정(seam) — 1차 검증에서 튜닝.
- **테스트**: `IgnoreDealUseCaseTest`(멱등·빈출 토큰→제안), `ReviewCallbackRouterTest.ignoreFromAllowedChatRoutesToLearning`, `TelegramAlertSenderTest.includesIgnoreButtonForTheDeal`, web `present.test`(KEYWORD_SUGGEST 표시).
- **남은 것**: 원-탭 수락(KEYWORD_SUGGEST를 승격하면 자동으로 exclude_keywords에 추가)은 후속 — `alert_policy.period_months`가 NOT NULL이라 정책 없는 variant엔 upsert가 복잡하고, KeywordSuggester 계약이 "수락 시에만 갱신, 판단은 사람"이라 지금은 제안+수동 추가로 충분.
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
- **✅ 상태→ENDED 절반 해소(2026-07-09)**: 신규 `ReprocessDealStatusUseCase.reprocessEndedDeals()` — 링크된 **모든** 원문이 SOLD_OUT/DELETED면 `deal_event`를 ENDED로, last_seen을 종료 근거 시각으로 단조 갱신. `findUnprocessed`·`IngestDealsUseCase` **무수정**(additive: `DealEventEntity.applyStatusChange`·`DealEventRepository.findByStatusIn`만). Testcontainers 4케이스(단일 품절·DELETED·다중소스 중 하나 ACTIVE 유지·전부 ACTIVE 무변). **잔여(여전히 열림)**: ~~① 가격변화 재처리~~ **해소(2026-07-10)**: 신규 `ReprocessDealPricesUseCase` + 순수 `PriceRefresh`. `priceFirst`·`firstSeen`·`status` 불변, `priceLast`="지금"(**활성** 원문 중 최신 관측 — 방금 품절된 최저가는 "지금 가격"이 아니다), `priceMin`="지나간 기회"(품절 원문도 포함), `lastSeen` 단조, 변화 없으면 미기록. 기존 core 파일 무수정(`DealEventMapper.toDomain`으로 crossVerified 복원). 스모크 5-1c가 `999000/899000/899000` 종단 증명. / ② `captured_at>last_seen` 변경 감지기(전수 스캔이라 정확성은 무관, 효율 seam), ③ **최초 수집 시 이미 품절인 원문 → 품절된 딜에 알림이 나간다**(2026-07-10 실측). `IngestDealsUseCase:137`이 원문 상태와 무관하게 `DealStatus.ACTIVE`로 딜을 만들고 `:110`에서 곧바로 `alertEvaluation.evaluate`를 태운다. 파이프라인 순서(ingest → 가격 → 종료) 덕에 **DB는 같은 틱에 자가치유**되지만(스모크 5-3이 증명) **알림은 이미 나간 뒤다.** 실측 로그: `[STUB alert] intensity=GOOD price=700000` → 직후 `deal_event.status = ENDED`. 지금은 `StubAlertSender`라 로그뿐이지만 **봇 토큰(Q-20)이 켜지는 순간 실전송된다.** 고치려면 `ingestOne`이 `post.getStatus()`를 보고 초기 상태를 정하거나(또는 종료될 딜의 알림을 억제), ④ **애매/스킵 글 재처리 중복 — 실측됨(2026-07-10)**: `findUnprocessed()`는 `deal_event_source` 링크가 없는 원문을 미처리로 본다. 매칭 실패 원문(UNKNOWN·CANDIDATE)은 딜을 만들지 않아 링크도 생기지 않으므로 **매 틱 다시 `review_queue_item`에 쌓인다.** 스모크 5-1e가 2초 주기에서 재현한다 — 운영 60초 주기면 원문 하나당 **하루 1,440행**. 리뷰 큐 조회(Q-15)는 같은 근거를 접어 `occurrences`로 세어 보여준다(**숨기지 않는다** — 조용히 지우면 결함이 사라진 것처럼 보인다). 그래도 승격·기각은 이게 고쳐져야 가능하다: 하나를 처리해도 나머지 N-1개가 남는다. 고치려면 처리 마커(`processed_at`)나 큐 업서트가 필요한데 둘 다 core 기존 파일/스키마다. — **②④는 core 기존 파일 수정이 필요해 상대와 조율. ③은 ✅해소(2026-07-11, 아래 실측 정정).** / ~~⑤ 배치 오케스트레이션~~ **해소(2026-07-10)**: `adapter/scheduler/PipelineScheduler`가 `ingestPending()` → `reprocessPriceChanges()` → `reprocessEndedDeals()` 순으로 주기 호출(`core.pipeline.interval-ms`, 기본 60s). 종료가 마지막이라 닫히기 직전의 마지막 가격까지 반영된다. `@EnableScheduling`은 신규 `SchedulingConfig`에. 단계별 예외 격리 + `initialDelay=interval`로 `@SpringBootTest` 오염 방지. 매 틱 `PipelineTickReport` 카운터(OBS-02, Q-57).
- **③의 이음새 실측(2026-07-10)** — "core 기존 파일이라 조율"이 **이번엔 참이다.** Q-46·Q-48·Q-50처럼 신규 파일로 우회되는지 검증했고, 안 된다:
  - 알림은 `IngestDealsUseCase.ingestOne` 안에서 **동기로** 발화한다(`alertEvaluation.evaluate(...)`가 `sources.save(...)` 바로 다음 줄). 파이프라인이 같은 틱에 `ENDED`로 자가치유해도 알림은 이미 나갔다.
  - 고칠 수 있는 지점은 셋뿐이고 **전부 상대의 기존 파일**이다: `IngestDealsUseCase.candidateFrom`(초기 상태를 `post.getStatus()`에서 정한다 — **가장 정직한 한 줄**) · `EvaluateAlertOnDealUseCase.evaluate`(발화 전 원문 상태 확인) · `AlertDispatcher`(발송 직전 억제).
  - **신규 파일만으로 되는 유일한 길**은 `AlertSender` 포트에 데코레이터를 끼우는 것이다(`AlertMessage.deal`이 `site`·`sourceUrl`을 지니므로 발송 시점에 `raw_deal_post.status`를 조회해 억제할 수 있다). 그러나 `@Primary` 자기참조나 `BeanPostProcessor`가 필요하고, **상대의 알림 경로가 그들의 파일 변경 없이 조용히 달라진다.** 그건 소유권 규칙의 정신을 우회하는 것이라 하지 않았다.
  - **권고(상대에게)**: `candidateFrom`의 `DealStatus.ACTIVE`를 `post.getStatus()` 기반으로 바꾸는 한 줄. 그러면 종료 딜은 애초에 `ENDED`로 태어나고 `AlertEvaluator`가 자연히 걸러낸다. 스모크 5-3이 이미 자가치유를 관찰하고 있으니, 거기에 "알림이 나가지 않았다"는 단언만 더하면 회귀가 잠긴다.
  - **✅ 해소(2026-07-11, 우리가 수정 — 사용자가 core 소유권 조율)**: **권고의 전제가 틀렸다.** "ENDED로 태어나면 `AlertEvaluator`가 자연히 걸러낸다"고 적었지만, 재현해 보니 **`AlertEvaluator`는 `deal.status()`를 한 번도 보지 않았다**(전수 grep 0). 우리가 적어둔 권고도 가설이었다(CLAUDE.md "실측 기록도 가설이다"의 사례). 그래서 **한 줄이 아니라 두 지점**을 고쳤다: ① `candidateFrom`이 `DealStatus.fromRawPostStatus(post.getStatus())`로 초기 상태 결정(SOLD_OUT/DELETED→ENDED, null·그 외→ACTIVE) ② `AlertEvaluator.evaluate`가 ENDED 딜을 early-return으로 억제. 둘 중 하나만으로는 결함이 안 닫힌다(candidateFrom만 고치면 status를 안 보는 Evaluator가 여전히 발화, Evaluator만 고치면 딜이 ACTIVE로 태어나 가드 미발동). 문자열 종료 집합은 `DealStatus.ENDED_RAW_STATUSES` **정본 하나**로 모으고 `ReprocessDealStatusUseCase`도 이걸 참조(사본 제거). 관통 테스트 `IngestDealsUseCaseTest.initiallySoldOutPostIsBornEndedAndNotAlerted`(스파이 `AlertSender`로 **send 0회** + `status==ENDED`), 도메인 `AlertEvaluatorTest.endedDealNeverAlerts`, 매핑 `DealStatusTest`. → `docs/99`.
- **④ ✅ 해소(2026-07-12, 우리가 수정 — core 소유권 조율)**: 미상 원문은 딜로 링크되지 않아 `findUnprocessed()`가 매 틱 다시 반환 → 예전엔 `enqueueForReview`가 매번 새 `review_queue_item` 행을 넣었다(60초 주기면 원문 하나당 하루 1,440행). 이제 `IngestDealsUseCase.upsertReviewItem`이 `dedup_key`(UNCLASSIFIED=`u:`+원문id, OUTLIER_LOWER=`o:`+딜id)로 **한 행에 접고** 재적재를 `occurrences` 컬럼으로 센다(V5 마이그레이션 + R5 롤백). 읽기 모델(`GetReviewQueueUseCase`)은 이제 그 컬럼을 직접 읽는다 — 예전엔 `(type,payload)`로 접어 `count(*)`로 셌다(쓰기가 무한히 늘었다). 스모크 5-1e가 "정확히 1행 + occurrences≥2"로 종단 검증, 롤백 드릴이 R5를 검증. 테스트: `IngestDealsUseCaseTest.repeatedIngestOfSameUnclassifiedFoldsIntoOneRow`·`GetReviewQueueUseCaseTest.pendingItemReadsOccurrenceColumnAndTimestamps`. **이로써 Q-15의 선결 ③(같은 근거가 한 행)이 충족됐다.** **남은 것**: resolve-then-recur — 기각된 근거가 다시 와도 `dedup_key`가 unique-global이라 기각 행의 occurrences만 조용히 증가한다(재오픈 안 함). Q-15 승격·기각 착수 시 "PENDING만 dedup" 또는 재오픈 규칙으로 정합(보수적 기본값이라 지금은 수용).

## [부분해소] Q-28. 제외키워드 표본 적용 — per-product는 배선됨, global_setting·⚠️LABEL 가시성은 후속
- **✅ per-product 배선 해소(2026-07-21, 프론트 지휘로 생산자가 생김)**: `alert_policy.exclude_keywords`(text[])를 매핑하고, 걸리는 딜을 **기준가·신호·알림·구매성적 전 통계에서** 뺀다. 소비처(네 유스케이스)와 생산자(정책 패널의 제외 키워드 입력)가 동시에 생겼다 — K(Q-48 ①)와 같은 선례. 구현:
  - **해석 정본 한 곳** = 신규 `VariantExcludeKeywords`(application). 매 조회마다 `alert_policy.exclude_keywords`를 읽어 `DealEventSource→raw_deal_post.title`에 대고 순수 도메인 `ExcludeKeywordPolicy`(여기 오기 전 **소비처 0**이었다)로 판정, 걸리면 매핑 전 엔티티 단계에서 뺀다. **왜 조회 시점인가**: 제외 목록은 사용자 손잡이라 언제든 바뀐다 — 저장 시점(ingest)에 태그로 굳히면 나중에 넣은 키워드가 이미 들어온 딜에 소급되지 않는다. 그래서 deal_event에 보존하지 않고 매 조회에 지금 목록으로 판정한다(놓쳤던 "제목 접근"은 소스 조인으로 풀린다).
  - `GetBenchmarkUseCase`·`GetSignalUseCase`·`EvaluateAlertOnDealUseCase`·`RecordPurchaseUseCase`가 전부 `demandScope.scope(...)` **앞에** `excludeKeywords.filter(...)`를 건다 — 네 표면이 같은 표본을 봐야 화면이 서로 다른 사실을 말하지 않는다.
  - `AlertPolicySettings`(+`excludeKeywords`, 공백 제거·빈 값 탈락·중복 접기)·`AlertPolicyEntity`(`@JdbcTypeCode(ARRAY)`)·벌크 UPDATE에 포함·`AlertPolicyView`/`UpdateRequest`. web: 정책 패널에 쉼표 한 줄 입력(PUT 전체 교체라 **항상 보낸다** — 안 보내면 저장 때마다 목록이 사라진다).
  - 테스트: `GetBenchmarkUseCaseTest`(제외 시 n·최저가 변화 + **거울상** 무력화 방지), `AlertPolicySettingsTest`(정규화), `updatePreservesColumnsTheEntityDoesNotMap`(이제 exclude_keywords는 **갱신 대상**, demand_axis_filter만 보존 대상), 스모크가 미설정 `[]`·왕복을 종단 검증. web 165→170.
- **✅ ① global_setting 전역 제외 키워드 해소(2026-07-22)**: `global_setting`은 V1부터 있었으나 **엔티티도 REST도 없어 완전히 죽은 테이블**이었다(table-wiring·dead-columns 게이트가 둘 다 면제로 물고 있었다). 생산자·소비처가 동시에 생겨 살아났다.
  - **소비처**: `VariantExcludeKeywords.keywordsFor`가 **전역 ∪ 제품별** 합집합(재개 트리거가 지목한 그 seam 한 곳). 둘 중 하나만 걸려도 제외(보수적).
  - **생산자**: 신규 `GlobalExcludeKeywords`(global_setting['exclude_keywords'] 읽기/전체교체, generic jsonb라 네이티브 SQL) + `GlobalSettingsController`(GET/PUT `/api/v1/settings/exclude-keywords`) + web `settings/SettingsPage`('설정' 탭).
  - **정규화 정본 하나**: `ExcludeKeywordPolicy.normalize`로 추출 — per-product(`AlertPolicySettings`)와 전역이 같은 규칙을 써야 합칠 때 공백·중복으로 안 어긋난다(사본 금지).
  - **정직한 기본값**: 미설정→빈 목록(부재를 "전부 제외"로 안 읽음), 저장값 깨짐→빈 목록(전 표면이 이 조회를 타므로 통째로 안 죽인다).
  - 관통 테스트: 전역 키워드만으로(정책 없이) 기준가 표본에서 빠지는지 — 끊기면 "저장은 되는데 효과 없는 죽은 손잡이". + 왕복·정규화·부재·깨진값 4케이스, web 5케이스, 스모크 5-2c(왕복+정규화 종단). 게이트: 두 allowlist에서 global_setting 면제 삭제(낡은 면제를 게이트가 차단했다).
- **남은 것(여전히 열림)**: ② **⚠️LABEL 가시성** — C-5는 LABEL도 통계 제외하되 ⚠️로 노출하라지만, 현재 전 키워드를 EXCLUDE로 다룬다(숨김).
- **② 설계 실측(2026-07-22, 착수 전 조사)** — **저장보다 표본 흐름이 진짜 비용이다**:
  - **저장은 싸다**: per-keyword mode를 문자열에 인코딩(`"label:리퍼"`)하면 한 필드가 값과 분류를 겸해 굳는다(금지). 대신 **평행 목록**(`alert_policy.label_keywords text[]` + global_setting의 `label_keywords` 키)이 보수적·가역적(V/R 한 쌍).
  - **🔴 비싼 쪽**: LABEL은 "**통계에서만** 빼고 **화면엔 남긴다**"이다. 그런데 지금 구조는 `VariantExcludeKeywords.filter`가 **기준가 계산 전에** 딜을 통째로 제거한다 — 제거된 딜은 `BenchmarkView.cases`(사례 목록)에도 없다. LABEL 딜을 ⚠️로 보여주려면 표본이 **두 갈래**여야 한다: 통계용(clean만) / 표시용(clean + labeled). 즉 `BenchmarkCalculator.compute`가 두 목록을 받아야 하고, 그 호출부(GetBenchmark·GetSignal·RecordPurchase·IssueReportCards + 테스트 다수)가 함께 바뀐다.
  - **싸게 흉내 내면 가치가 없다**: 딱지로 "LABEL로 N건 뺐다"만 내는 건 이미 있는 제외 건수 딱지와 사실상 같다(딜을 **볼 수** 없으면 ⚠️의 뜻이 없다). 미상 큐로 돌리는 것도 안 맞는다 — 제외 판정은 **조회 시점**인데 큐 항목은 ingest 시점 생성이라 설계가 어긋난다.
  - **결론**: ②는 "컬럼 하나 추가"가 아니라 **표본 흐름을 두 갈래로 여는 변경**이다. 되돌리기 어렵진 않으나(계산기 시그니처는 seam) 리플이 넓어 **의도적으로 착수할 것**.
- **재개 트리거**: 실 데이터에서 "제외 키워드가 뭘 먹었는지 눈으로 봐야겠다"가 실제로 필요해질 때(제외 건수 딱지가 그 신호를 준다 — 딱지가 자주 크게 뜨면 그때가 그때다). 착수하면 위 두 갈래 설계로.

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
- **✅ (b) 해소 2026-07-23**: `site_poll_state`(V12)에 collector가 **성공한 폴링만** 적고, core `ObservationClock`이 그중 **가장 이른** 값을 읽어 `SignalCalculator`에 넘긴다. 배선 뮤테이션으로 RED 증명 + 스모크 5-1 종단 단언(딱지 유무 양방향).
  - **아래 2026-07-10 정정의 마지막 문장은 틀렸다.** `lastPoll = now`일 때 신선도가 "확신 한 칸"에 갇힌 게 아니라 **정반대**로 움직였다 — staleness가 벽시계를 따라 자라서 **수집이 멈춘 동안 딜이 늙은 것처럼 보이고** 신호등이 "딜 없음"으로 **거짓 강등**됐다. docs/03 3-2가 막으려던 "무지를 부재로 오독"이 그대로 일어나고 있었다. (막힘 사유 ①②는 둘 다 소유권 근거였고 2026-07-23 소유권 조항 폐기로 소멸.)
  - **남은 한계**: `SignalCalculator`는 스칼라 하나만 받으므로 **딜별 소스 사이트의 폴링 시각**을 못 본다. `deal_event_source.site`가 있으니 정교화는 가능하다 — 사이트별 staleness가 필요해지면(예: 한 사이트만 오래 죽어 있고 그 사이트 딜만 오래됐을 때) 그때 signature를 넓힌다. 지금은 min이 보수적(딜을 덜 탈락시킨다)이라 족하다.
  - **폴링 기록이 0행이면** 벽시계로 대신하고 "수집 기록 없음" 딱지를 단다(조용한 sentinel 금지).
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

## [부분해소] Q-62. PUR 상태기계 — 만료·**성적표 발급(CLOSED)**까지 배선. ARCHIVED만 남음
- **맥락**: `PurchaseState`는 `OBSERVING → REPORT_PENDING → CLOSED → ARCHIVED → OBSERVING`을 정의하고 `Purchase.expire()/close()/archive()/reactivate()`가 다 있다. 그런데 **프로덕션에서 상태를 쓰는 곳은 `RecordPurchaseUseCase`(항상 OBSERVING) 하나뿐이었다.** 상태기계에 전이가 있어도 부르는 사람이 없으면 그 전이는 존재하지 않는다.
- **해소된 부분(2026-07-10)**: `ExpirePurchaseObservationsUseCase`(신규 파일) — 관찰 기간이 끝난 구매를 `REPORT_PENDING`으로. `PipelineScheduler`가 **ingest보다 먼저** 부른다(ingest가 알림을 태우는데 PUR-03 "산 뒤 알림"은 OBSERVING에만 발화하므로). 스모크 5-2b가 종단 증명. 그전까지 ① "관찰 N일차"가 무한히 커지고 ② **3년 전 구매에도 계속 알림이 나갔을 것**이며 ③ 성적 집계 대기로 넘어가지 않았다.
- **✅ 성적표 발급 해소(2026-07-21) — `REPORT_PENDING → CLOSED`가 산다.** 그전까지 `ReportCardCalculator`(순수 도메인)는 성적을 계산할 줄 알고 테스트도 GREEN이었지만 **프로덕션 호출자가 0**이었다(Q-68의 거울상 — "호출자 0인 순수 함수"). 이제:
  - **저장소**: `report_card` 테이블(V10/R10) + `ReportCardEntity`/`ReportCardRepository`. 재발급 없음 = `purchase_id` 유니크(ReportIssueGate).
  - **발급 유스케이스**: 신규 `IssuePendingReportCardsUseCase` — REPORT_PENDING 구매마다 `ReportCardCalculator`로 성적표를 계산해 저장하고 `Purchase.close()`로 CLOSED 전이(벌크 UPDATE, state만 — expire와 같은 수법). 멱등·크래시 안전(발급 후 전이 직전에 죽어도 다음 틱에 전이만 마저). **표본은 record와 동일 스코프**(제외키워드→수요축→pricingSet), **기준가는 구매 시점 동결값**(PUR-02 `snap_benchmark_price`). 발급은 **quiet**(관통 알림 없음)라 텔레그램 토큰과 무관.
  - **배선**: `PipelineScheduler`가 **만료 바로 뒤·ingest 앞**에 부른다 — 방금 만료된 것까지 같은 틱에 닫아 뒤의 ingest가 CLOSED로 보아 "산 뒤 알림"에서 뺀다. 발급 수를 `PipelineTickReport.reportCardsIssued`로 싣고, 발급이 REPORT_PENDING을 드레인하므로 `purchasesExpired`를 `Δ(REPORT_PENDING)+발급`으로 재구성한다(카운터 오염 방지, docs/99).
  - **읽기 소비처**: `PurchaseObservation.reportCard`(CLOSED만 채움, 그 외 null) — `GetPurchaseObservationsUseCase`가 `findByPurchaseId`로 읽어 `GET /api/v1/variants/{id}/purchases`로 낸다. 없으면 report_card가 **write-only 테이블**(쌓이는데 볼 수 없던 review_queue_item의 거울상)이 될 뻔했다.
  - 관통 테스트 `IssuePendingReportCardsUseCaseTest`(발급→CLOSED·멱등·OBSERVING 제외), 배선 `PipelineSchedulerTest.issuesReportCardsAfterExpireAndFlowsCountIntoReport`(순서 + 카운트 흐름 + 재구성). domain-consumers allowlist에서 `ReportCardCalculator`·`ReportIssueGate` 삭제(이제 소비됨, 게이트가 낡은 면제를 잡았다).
- **남은 것**: ① **web 성적표 표시** — API는 `reportCard`를 내지만 web은 아직 안 그린다(별도 프론트 증분). REPORT_PENDING은 이제 매 틱 드레인돼 순간이지만, 그 순간의 문구는 여전히 "관찰 종료 · 성적표는 아직 발급되지 않습니다"가 옳다. ② `CLOSED → ARCHIVED`(PUR-06)·`ARCHIVED → OBSERVING`(재활성)은 호출자 0 — 아카이브 조건("다른 활성 관찰 없을 때") 판정처가 정해져야 한다. ③ 발급 게이트의 두 외부 조건(백필 배치 C-4·미분류 48h 유예)은 지금 보수적으로 true(대기할 상태가 없다) — 그 상태가 생기면 `canIssue`에 주입(seam은 유스케이스 한 줄).
- **잠정값(발급 규약)**: observedFrom = 최초 딜 firstSeen(Q-34와 동일), capturedAt ≤ 발급 검증은 `DealEvent.capturedAt` 부재로 미적용(Q-32) — 둘 다 record와 같은 잠정이라 성적이 구매 시점 판단과 일관된다. 삭제 3행 매트릭스(Q-30)는 발급과 직교(삭제 REST 착수 시).
- **재개 트리거**(남은 ARCHIVED): 아카이브 조건 판정처(다른 활성 관찰 집합)가 정해지고, 삭제 3행 매트릭스(Q-30)와 정합될 때.

## [열림] Q-61. SEC-03·SEC-04·SEC-07이 어느 보드에도 없었다 (Q-58과 같은 실패 모드)
- **맥락**: `docs/20`의 NFR ID 26개를 코드·테스트·스크립트·compose·CI에서 전수 grep했더니 **SEC-03·SEC-04·SEC-07이 참조 0**이었고, `docs/91`·`working-area` 어디에도 없었다. 보드에 없는 요구는 없는 일이 된다 — PERF·OPS에서 이미 한 번 겪었다(Q-58).
- **SEC-07 개인정보 최소화 — 지켜지고 있었으나 강제되지 않았다 → 해소(2026-07-10)**: 번개 응답에는 `uid`(판매자 식별자)·`location`(동 단위 주소)·`imp_id`(광고 추적자)가 온다. `parse_bunjang`은 불리언 셋만 담는다 — **신중해서 그랬을 뿐 계약이 아니었고**, `raw`는 `jsonb`라 한 줄이면 응답 전체가 들어간다. `tests/test_privacy.py`가 golden 전수로 잠갔다: 키 허용집합 + **값** 검사(키 이름을 `u`/`loc`으로 바꿔 우회해도 잡힌다). 뮤테이션으로 실제 FAIL 확인.
- **✅ SEC-03 해소(2026-07-21)**: **아웃바운드**(알림 발송)는 설정된 `TELEGRAM_CHAT_ID` 하나로만 나간다(`TelegramAlertSender`). **인바운드**(인라인 버튼 콜백)도 이제 화이트리스트로 거른다 — `ReviewCallbackRouter`가 `TELEGRAM_ALLOWED_CHAT_IDS`(비면 `TELEGRAM_CHAT_ID` 폴백, **둘 다 비면 아무도 허용 안 함** = 닫힌 기본값)에 든 chat의 명령만 처리하고, 밖의 명령은 거부·로그한다(`ReviewCallbackRouterTest.deniesCommandFromUnauthorizedChat`가 못박는다). 봇 명령(/status 등)은 아직 없으나 인라인 버튼 콜백이 유일한 인바운드 표면이라 커버됐다.
- **잠정값(SEC-04)**: extension ingest(고정 토큰·스키마 검증·레이트 리밋)는 **기능4** 범위. 확장이 없으므로 노출면도 없다.
- **재개 트리거**: SEC-03 — 텔레그램 어댑터가 존재하는 순간. 그 커밋에 화이트리스트·콜백 검증이 **함께** 들어가야 한다(`pre-deploy §A`가 `TELEGRAM_ALLOWED_CHAT_IDS`를 필수로 요구한다). SEC-04 — 크롬 확장이 존재하는 순간.

## [열림] Q-60. `guard.sh`는 스크립트 파일 안의 네트워크 호출을 보지 못한다
- **맥락**: `.claude/hooks/guard.sh`(PreToolUse)는 **Bash 명령 문자열**을 파싱해 `curl`/`wget` + 6개 대상 호스트를 차단한다. 그런데 `bash scripts/check-robots.sh`처럼 **스크립트를 실행하는 명령**에는 호스트도 `curl`도 나타나지 않는다. 훅은 그 안을 보지 않는다.
- **왜 이제 드러났나**: 2026-07-10 `scripts/check-robots.sh`(실 robots.txt 대조 도구)를 만들면서. 이 스크립트는 정의상 실 사이트로 나간다.
- **왜 훅을 고치지 않나**: 스크립트 내용을 파싱해 차단하려면 훅이 파일 시스템을 읽고 셸 문법을 이해해야 한다. **오차단이 조용히 작업을 마비시킨다**는 규칙상, 그런 휴리스틱은 이 프로젝트의 다른 규칙과 충돌한다. 훅은 결정론적인 것만 막는다.
- **잠정값 (다층 방어)**: ① 네트워크로 나가는 스크립트는 **자기 자신에게도 opt-in 게이트를 건다**(`ALLOW_REAL_ROBOTS=1`, collector의 `COLLECTOR_ALLOW_NETWORK`와 같은 패턴). ② 스크립트 상단에 "에이전트가 이걸 opt-in과 함께 돌리면 정지조건 위반"임을 명시. ③ 정지조건은 결국 **지침의 몫**이다 — 훅은 실수를 막고, 고의를 막지는 못한다.
- **재개 트리거**: 네트워크로 나가는 스크립트가 늘어나 opt-in 패턴이 흔들리면 — 그때 훅이 `bash *.sh` 실행을 **전부** 사람에게 확인받게 할지 검토(오차단 비용이 크다). seam = `.claude/hooks/guard.sh` 1곳.

## [해소 2026-07-24] Q-59. REL-03 폴링 커서 영속화 — D-3 확정 후 배선 완료
- **맥락**: `SiteState`(연속 실패 횟수·`stopped` 플래그·`next_attempt_at`)가 메모리에만 있어 컨테이너 재시작 시 초기화됐다. REL-03은 "재시작 내성: 폴링 커서 영속화"를 요구한다.
- **막고 있던 것**은 DB가 아니라 **차단당한 사이트의 재개 경로**(`decisions-needed` D-3)였다 — 그 결정 없이 커서를 영속하면 `stopped=true`가 디스크에 굳어 사이트가 영구히 죽었다.
- **✅ 해소**: D-3이 "설정/DB 값 수동 수정"으로 확정돼(2026-07-24) 착수. `site_poll_state`(V15, R15)에 `consecutive_failures`·`next_attempt_at`·`stopped` 컬럼 추가(`last_successful_poll_at`도 nullable로 완화 — 한 번도 성공 못 한 채 커서만 생기는 사이트가 있을 수 있다). core `SitePollStateEntity`는 이 세 컬럼을 매핑하지 않는다(core는 안 씀, 매핑하면 다음에 혼동만 준다) — 관측시계(`ObservationClock.earliestSuccessfulPoll`, SQL `min()`)는 nullable 완화에 영향받지 않는다.
- collector `SitePollStateSink`가 `load_states()`(기동 시 복원)·`persist_states()`(매 사이클 전체 커서 저장, `last_successful_poll_at`만 단조 증가)를 제공하고 `__main__.main()`이 기동 시 이를 로드해 `states`/`market_states`를 seed한다. **재개는 재시작을 전제한다** — 운영자가 DB 행을 직접 `UPDATE ... SET stopped=false, next_attempt_at=null, consecutive_failures=0`한 뒤 컨테이너를 재시작해야 반영된다(라이브 리로드 아님). 별도 명령·API 없음.
- **종단 검증**: `test_a_blocked_site_stays_stopped_across_restart_until_manually_resumed`(collector, `main()`을 두 번 호출해 재시작 시뮬레이션) — 차단 → 재시작해도 여전히 멈춤 → DB 수동 UPDATE → 재시작하면 실제로 재개돼 딜이 들어옴. `test_a_failed_poll_does_not_advance_the_observation_clock`은 계약이 바뀌어 갱신(예전: 실패 사이트는 행 자체가 없음 → 이제: 행은 있되 성공 시각만 null).
- **관련**: `decisions-needed` D-3(해소·decision-log 참조).

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
- **한 것(2026-07-12, ②③ 해소)**: core 소유권 조율 후 `IngestDealsUseCase.ingestPending()`이 `void`→`IngestReport`
  (confirmed·candidate·unknown·rejected·skippedNoPrice·firstAlertsSent — 부류가 다른 사실을 합치지 않는다).
  `PipelineScheduler`가 ingest를 `Supplier<IngestReport>`로 받아 `PipelineTickReport.ingest`로 실어 매 틱 로그에
  `matched[confirmed=.. candidate=.. unknown=.. rejected=.. skippedNoPrice=..] firstAlertsSent=..`를 남긴다.
  ③ 첫 알림 발송 수는 `EvaluateAlertOnDealUseCase.evaluate`가 이미 반환하던 `DispatchOutcome==SENT`로 센다.
  smoke가 종단으로 `matched[confirmed=1 …]`를 잠근다. 테스트: `PipelineTickReportTest`·`IngestDealsUseCaseTest.
  reportCountsMatchTiersAndFirstAlerts`·`PipelineSchedulerTest`.
- **~~후속 알림 발송 수 미집계~~ 해소(2026-07-21)**: `sendFollowUps`가 int를 반환하는데 `PipelineScheduler`가 그걸 `BiConsumer`로 받아 **버리고 있었다**(값이 흐르다 버려지는 절반 카운터). `BiFunction`으로 바꿔 `runStepReturning`으로 붙잡고 `PipelineTickReport.followUpPriceChangedSent·followUpEndedSent`로 싣는다 — 첫 알림과 부류가 다르고 후속끼리도 PRICE_CHANGED·ENDED를 가른다(ENDED가 몰리면 딜 대거 종료, PRICE_CHANGED가 몰리면 가격 이동 — 뜻이 다르다). 로그: `followUpsSent[priceChanged=.. ended=..]`. 테스트: `PipelineSchedulerTest.followUpSendCountsFlowIntoReport`(값이 리포트로 흐름을 관통), `PipelineTickReportTest.reportsFollowUpSendCountsByKind`, 스모크가 종단으로 마커를 잠근다.
- **남은 것**: ① OBS-01 구조화 로그(JSON) — 지금은 텍스트(로그 수집기 붙일 때, core 전체 형식 변경이라 조율). ④ **API 쿼터**는 네이버 키 대기(Q-3).
- **잠정값**: 텍스트 로그 + 틱 단위 카운터. `docker logs`로 읽는다.
- **재개 트리거**: ①은 로그 수집기(운영 배포)를 붙일 때 — core 전체 로그 형식을 바꾸는 일이라 상대와 조율. ②③은 use case 반환값 변경이 필요하니 **core 기존 파일 수정**이라 조율 대상. ④는 Q-3.

## [해소] Q-56. 단계 실패 카운터 + 연속 실패 관리 알림(OBS-03) 완성
- **맥락**: `PipelineScheduler.runStep`은 한 단계(ingest·reprocess)가 던져도 다른 단계와 다음 주기를 살리려고 예외를 잡는다. 잡은 뒤에는 `log.error`로 단계 이름과 함께 남기고 계속한다. **격리는 침묵이기도 하다** — DB 스키마 불일치·락 충돌 같은 지속 실패가 나면 파이프라인은 도는 척하며 아무것도 처리하지 않는데, `docker logs`의 `log.error` 한 줄을 grep하지 않으면 모른다.
- **✅ 카운터 해소(2026-07-21)**: `stepFailures`를 `runStep`·`runStepReturning`의 catch에서 세어 `PipelineTickReport.stepsFailed`로 매 틱 로그에 싣는다(`stepsFailed=N`). 건강한 틱은 `stepsFailed=0`이라 **비-0이 대비로 드러난다** — 이제 틱 요약 한 줄만 봐도 "도는 척" 틱이 정상 틱과 구별된다. 필드 하나로 세는 게 안전한 이유: `@Scheduled(fixedDelay)`는 틱이 겹치지 않는다(이전 틱 완료 후 다음 시작). 매 틱 0으로 리셋. 테스트: `PipelineSchedulerTest.{failedStepIsCountedInReport, idleTickStillReports(=0)}`, `PipelineTickReportTest.reportsStepFailureCount`, 스모크가 `stepsFailed=0` 종단 마커.
- **✅ 관리 알림 해소(2026-07-21, OBS-03)**: 신규 `AdminNotifier` 포트 + `PipelineHealthMonitor`(연속 실패 임계 넘으면 push, **임계에 처음 닿는 순간만** — 스팸 방지, 건강해지면 리셋, 임계 기본 3 = collector `SINK_FAILURE_LIMIT`과 정합). `PipelineScheduler`가 매 틱 `healthMonitor.onTick(healthy)`를 부른다 — `healthy = 스냅샷 왕복 + stepsFailed 0`이라 **지속 DB 장애(스냅샷 실패로 리포트도 못 내는 틱)도** 신호가 잡힌다. 발송은 `StubAdminNotifier`(기본, 로그) / `TelegramAdminNotifier`(`telegram.enabled=true`, 딜 알림과 같은 chat에 🔧 접두). 테스트: `PipelineHealthMonitorTest`(임계·리셋·재알림·스팸방지 4건), `PipelineSchedulerTest.feedsHealthSignalEachTick`(정상 true·단계실패 false·DB단절 false), `TelegramAdminNotifierTest`, `TelegramSenderWiringTest`(빈 선택). **딜 알림과 다른 포트다** — 시스템 자신의 건강이라 `AlertMessage`(딜 기반)가 아니다.

_(Q-55. SEC-05 크기 상한 — **해소됨 2026-07-10**: `pipeline/ingest.py`에 상한 4종(title 300자 · url 2000자 · post_id 64자 · raw 256KiB, **바이트**로 잼). **자르지 않고 거절**한다 — 잘린 제목은 정상 제목의 얼굴을 한 거짓말이라 매칭(BM-03)을 조용히 망친다. 한 건이 비대해도 배치 전체를 버리지 않는다(원칙 3). 무엇을 왜 버렸는지 `oversized` 이벤트로 남기고 `cycle.skipped` 카운터에 0도 센다. 상한값 근거: golden 89건 실측 최대(title 62 · url 75 · post_id 11 · raw 57B)의 수 배 — 전수 대조로 오차단 0건 확인. 잠정값이며 seam은 그 상수 4개. 여기서 제거.)_

## [열림] Q-54. 적재에 실패한 사이클의 딜은 버려진다 (재시도 큐 없음)
- **맥락**: `sink.upsert_all`이 던지면(DB 재시작·연결 끊김) 그 사이클에 파싱한 딜은 **메모리에서 사라진다**. 프로세스는 `sink_error` 이벤트로 유실 건수를 남기고 다음 틱을 계속한다.
- **왜 대체로 괜찮은가**: 15초 뒤 같은 목록 페이지를 다시 긁으므로 그 딜들은 **다시 잡힌다**. 목록에서 밀려날 만큼(수 페이지) 트래픽이 몰린 사이에만 영구 유실이다. 게시판당 1req/min 하한을 감안하면 실제 창은 수 분이다.
- **잠정값**: 재시도 큐·디스크 버퍼 없음. 연속 3회 실패하면 프로세스가 exit 1로 내려온다(`SINK_FAILURE_LIMIT`) — 유실이 누적되기 전에 멈추는 쪽을 택했다.
- **재개 트리거**: 1차 검증에서 `sink_error`가 실제로 관측되면 — 그때 유실 건수와 재폴링 회복률을 보고 판단한다. 커서 영속화(REL-03, D-3 선결)를 하게 되면 실패한 배치를 디스크에 남기는 것도 같이 검토.

## [해소 2026-07-12] Q-53. `currentPrice = 0`이 "미확립"과 "공짜"를 구분하지 못한다
- **해소(근원)**: core 소유권 조율(2026-07-11) 후 재개 트리거대로 **core에서** 고쳤다. `CurrentPriceProvider.currentPriceFor`→`Long`(nullable), `StubCurrentPriceProvider`는 `null` 반환. `BenchmarkView.currentPrice`→`Long`, `BenchmarkCalculator.leg`가 `currentPrice==null`이면 leg를 null로(갭을 지어내지 않음). 알림 경로도 막혔던 놓침을 명시 가드로 정직화 — `AlertEvaluator.qualifiesColdStart`가 `currentPrice==null`이면 false(근거 없이 발화 안 함, 예전엔 0 비교로 조용히 놓침). web은 sentinel 0 해석을 **null 내로잉**으로 교체(`present.ts` `currentPriceUnavailable`, `Gauge` 게이트, `types.ts`). 테스트: `BenchmarkCalculatorTest.currentPriceUnavailableYieldsNullGapNotMinusHundredPercent`·`AlertEvaluatorTest.noneTierColdStartDoesNotFireWhenCurrentPriceUnavailable`·web `present`/`DecisionPage`/`App`. 남은 잔재 없음 — 네이버 키(Q-3) 발급 시 실 어댑터가 실값을 반환하면 그대로 갭이 산출된다.
- (이하 원래 맥락 — 왜 이 문제였나)
- **맥락**: `StubCurrentPriceProvider`(core)는 네이버 키 미발급 상태에서 현재가로 **0**을 반환한다(Q-3). `BenchmarkCalculator.leg()`는 `won = currentPrice - reference`를 그대로 계산하므로, 기준가 820,000원이면 API가 `gap.vsBenchmark = {won: -820000, pct: -100.0}`을 내보낸다. **타입만 보면 "지금 100% 싸다"는 정상 응답이다.**
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

## [해소] Q-49. `POST /api/v1/products` 서버측 검증 — 잘못된 입력이 이제 400 + 도메인 코드
- **맥락**: `RegistrationController`는 `@Valid`를 쓰지 않고 `RegisterProductCommand`에도 컴팩트 생성자 검증이 없었다. 빈 `name` POST → `DataIntegrityViolationException` → 500, `axes`/`variants`/`demandAxisMode` null → NPE·NOT NULL → 500. 클라이언트(`buildCommand`)가 가렸지만 **curl로 직접 치면 500**이었다(검증은 방어가 아니라 편의).
- **✅ 해소(2026-07-21, core 소유권 조율)**: `RegisterProductUseCase.validate(cmd)`가 저장 전에 막는다 — `InvalidRegistrationException`(`CODE=REG_INVALID_PRODUCT`)를 `ApiExceptionHandler`가 **400 + {code,message}**로 낸다(다른 도메인 예외와 같은 경로). 규칙: **빈 이름·빈 variant·null 축·null 모드는 거절, 빈 목록(axes)·null 별칭은 허용**(별칭은 선택이라 null=없음으로 저장). 모드 기본값은 코드로 복제하지 않는다 — 정본은 DB DEFAULT('GROUPED')이고 사본은 드리프트한다(그래서 null 모드는 지어내지 않고 400).
- **테스트(B(A()) 관통)**: `RegisterProductUseCaseTest`(빈 이름·빈 variant·null 축·null 모드→예외 + 아무것도 저장 안 됨, null 별칭→정상 저장), `RegistrationControllerTest.invalidCommandReturns400WithDomainCode`(HTTP 400 + `REG_INVALID_PRODUCT` — 유스케이스 테스트는 예외만, 핸들러는 매핑만 증명하므로 관통 테스트를 따로 둔다), 스모크가 종단으로 빈 이름 POST → 400 + 코드를 단언.
- **남은 것**: 없음. `spring-boot-starter-validation`(`@Valid`)은 여전히 미사용 — 유스케이스 검증이 한 곳(정본)이라 애노테이션으로 흩지 않는다.

## [열림] Q-48. 알림 정책(REG-03) — REST는 생겼으나 **엔티티가 매핑하지 않는 필드 셋**이 남았다
- **해소된 부분(2026-07-10)**: `AlertPolicyController`(GET/PUT `/api/v1/variants/{id}/alert-policy`) + `AlertPolicySettingsUseCase` + `AlertPolicySettings`(순수 검증) + `InvalidAlertPolicyException` + `AlertPolicyExceptionHandler` — **전부 신규 파일**, core 기존 파일 무수정. 이전까지 `alert_policy`에는 **프로덕션 writer가 없었다** — `EvaluateAlertOnDealUseCase`가 읽기는 했으나 행이 영원히 생기지 않아 확정본 §107의 "OR [사용자 목표가 이하]" 트리거와 방해금지(AL-04)가 발화할 수 없었다. 스모크 5-1d가 `intensity=TARGET`으로 정책이 판정에 닿는 것을 증명한다.
- **남은 것**: `AlertPolicyEntity`가 `k_display`·`exclude_keywords`·`demand_axis_filter`를 **매핑하지 않는다**(⚠️라벨 토글도 컬럼 부재). 그래서 REG-03의 6개 항목 중 넷(targetPrice·기간 P·quiet hours 2개)만 저장된다. 갱신은 **벌크 UPDATE**라 미매핑 컬럼을 보존한다(delete+insert였다면 DB 기본값으로 되돌아가 매핑을 붙이는 날 데이터가 사라진다 — `updatePreservesColumnsTheEntityDoesNotMap`이 못박는다).
- **또 하나**: 미설정 variant의 GET은 `periodMonths: null`을 낸다. 알림 판정이 쓰는 기본 6개월은 `EvaluateAlertOnDealUseCase`의 **private 상수**라 어댑터가 읽을 수 없다. 지어내 채우면 그 값이 세 번째 사본이 되고 사본은 드리프트한다.
- **잠정값**: 위 넷만. web 화면은 붙였다(`policy/AlertPolicyPanel`, 판단 화면 안 — "지금은 아니다"의 다음 행동이 "그럼 얼마면 알려줘"라서). **없는 손잡이는 그리지 않는다** — 그리면 저장되는 줄 안다. 미설정이면 "목표가 알림은 발화하지 않습니다"라고 말하고, 판정 기간의 시스템 기본값은 **숫자로 말하지 않는다**(과대약속 금지 + 세 번째 사본 금지).
- **재개 트리거**: ① `AlertPolicyEntity`에 세 컬럼이 매핑되어야 한다 ② `DEFAULT_PERIOD_MONTHS`가 한 곳에서 소유되어 어댑터도 읽을 수 있어야 한다 ③ ~~예외 핸들러가 한 곳으로 합쳐져야 한다~~ **✅ 해소(2026-07-12)**. (구현 수단은 여러 가지다 — 위 셋은 "무엇이 참이 되어야 하는가"이지 방법이 아니다.)
- **③ 해소(2026-07-12, core 소유권 조율)**: `AlertPolicyExceptionHandler`(별도 `@RestControllerAdvice`, "소유권 정리되면 합쳐라"는 자기 주석 포함)를 `ApiExceptionHandler`로 합치고 삭제했다. `InvalidAlertPolicyException`→400 매핑은 그대로. 핸들러가 한 곳이라 "어디를 봐야 할지" 드리프트 위험 제거. 알림 정책 테스트 GREEN.
- **✅ ① `k_display` 해소(2026-07-16, 프론트 지휘로 생산자가 생김)**: 세 컬럼 중 **K만** 매핑했다 — 소비처(기준가 tier)와 생산자(정책 패널)가 동시에 생겼기 때문이다. `AlertPolicyEntity.kDisplay` 매핑 + `AlertPolicySettings.kDisplay`(3~10 검증, 기본값 정본 `DEFAULT_K_DISPLAY=5`) + 벌크 UPDATE에 포함. 읽기는 신규 `VariantBenchmarkParams`가 **해석 정본 한 곳**: `from(policy)`가 "미설정이면 기본 K"를 해석하고, `of(variantId)`는 읽어서 그걸 부른다(이미 정책을 읽은 `EvaluateAlertOnDealUseCase`는 `from`을 직접 써 중복 조회 회피 — 두 경로가 각자 해석하면 한쪽이 조용히 다른 K를 쓴다). `GetBenchmark`·`GetSignal`이 variant별 params를 쓴다. GET은 미설정이라도 K를 **숫자로** 낸다(정본이 상수 하나라 사본이 안 생긴다 — ②와 다른 점). web: 정책 패널에 K 선택 + 맞바꿈 안내, `PolicyForm.kDisplay`(PUT은 전체 교체라 **항상 보낸다** — 안 보내면 저장할 때마다 K가 5로 리셋). 테스트: `GetBenchmarkUseCaseTest.userKDisplayMovesTheTier`(같은 5건인데 K=10이면 SPARSE+사례), 스모크가 미설정 기본 5·왕복 8·범위 밖 400을 종단 검증. `updatePreservesColumnsTheEntityDoesNotMap`은 이제 남은 미매핑 둘(`exclude_keywords`·`demand_axis_filter`)을 지킨다.
- **✅ ① `exclude_keywords` 해소(2026-07-21) · `demand_axis_filter`·②는 여전히 열림**: ① `exclude_keywords`는 Q-28(per-product 표본 적용)로 매핑·소비·생산자까지 살았다 — 이제 `updatePreservesColumnsTheEntityDoesNotMap`이 지키는 미매핑 컬럼은 `demand_axis_filter` **하나**다. 그건 Q-66 ①(SPLIT 분포 분리)의 수요축 **필터**가 생겨야 산다(현재 수요축은 `deal_event.demand_axis_value`로 분리하고, `alert_policy.demand_axis_filter`는 "이 variant에서 어느 축값만 볼지" 사용자 선택 손잡이라 아직 소비처가 없다). 매핑만 하면 "저장되는데 안 쓰이는" 손잡이라 원칙 위반 — **소비 기능과 함께** 매핑한다(K·제외키워드가 선례다). ② `DEFAULT_PERIOD_MONTHS` 단일소유는 **어댑터가 그 값을 보고할 소비처가 있을 때** 한다(지금 GET은 미설정 시 기간을 숫자로 말하지 않기로 결정 — 과대약속 금지). 소비처 없이 상수만 옮기는 것은 투기다.

## [부분해소] Q-46. 조건 태그 도달·표시·하한 취급 완료 — 알림 본문만 텔레그램(Q-20) 대기
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
- **✅ ① 해소(화면이 태그를 말한다)**: `DealEvent`에 `appliedConditions` 필드가 생겼고(`DealEventMapper`가 읽기전용 컬럼 매핑에서 복원), `BenchmarkCalculator.toRef`가 `BenchmarkView.DealRef.conditions`에 실어 낸다. web `DecisionPage`가 각 사례에 `conditionsSuffix(deal.conditions)`("· 조건부: 카할")를 붙이고, 미상 큐(`review/present.ts`)는 `배송비미상`이면 "실제 결제가는 더 높습니다"까지 말한다. **알림 본문**만 남았다 — 발송이 스텁(텔레그램 미발급, Q-20)이라 사용자에게 안 닿으므로 **어댑터를 만드는 커밋에 함께** 넣는다(지금 지어 넣으면 검증 못 하는 죽은 문구다).
- **✅ ② 해소(배송비미상 = 하한, 표본에서 제외)**: `DealSets.pricingSet`이 `!d.hasCondition(SHIPPING_UNKNOWN)`로 값 통계에서 뺀다 — 발생·신호 집합엔 남긴다(실제 딜이고 가격만 못 믿는다). `BenchmarkCalculator`가 median·tier보다 먼저 `pricingSet`을 부른다. **컬럼→매퍼→계산기 종단**을 `GetBenchmarkUseCaseTest.shippingUnknownDealIsExcludedFromBenchmarkThroughTheColumn` + 거울상이 잠근다(2026-07-21 — 그전엔 `DealSetsTest`가 순수 함수만 잠갔다: 손으로 만든 `DealEvent`라 매퍼·계산기 배선이 끊겨도 GREEN이었다). 표식 정본은 collector(`price.py: SHIPPING_UNKNOWN`), 사본은 `DealTags`, `check-tag-contract.sh`가 CI에서 둘의 일치를 강제.
- **남은 것**: 알림 본문의 조건 표시(위 ①, Q-20 텔레그램 어댑터와 함께). 표본 편향은 해소됐다 — **실 폴링 전 필수였던 ②는 닫혔다.**

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

## [열림] Q-65. `원 없는 숫자` 폴백이 아직 `DDR5 5600`을 5,600원으로 읽는다
- **맥락**: `_BARE`(4자리+ 숫자)는 golden 49개 제목에서 **한 번도 돌지 않는다** — 검증된 적 없는 가지였고, 그 안에 `RTX 5070` → 5,070원, `RTX 4090` → 4,090원, `5600X` → 5,600원이 있었다(2026-07-10 실측). 그래픽카드는 핫딜 최빈 품목이다.
- **잠정값**: 라틴 문자에 붙은 숫자를 거부한다 — 앞이 `RTX `(문자+공백)이거나 문자 인접, 뒤가 문자면 모델명이다. 한글 뒤의 숫자(`라면 13490`)는 그대로 가격이다(확정본 경계 케이스 "원 없는 숫자" 유지).
- **🔴 남는 위험**: `DDR5 5600 램` → **5,600원**. 숫자로 끝나는 시리즈명(`DDR5`) 뒤 공백 뒤의 숫자는 못 가른다. `라이젠 7 5600` 도 마찬가지다(`7 `이 숫자+공백).
- **왜 지금 못 정하나**: 더 좁히면 `라면 13490`·`아이폰 15 899000` 같은 진짜 가격을 잃는다. **어느 쪽이 실제로 흔한지 실 제목 표본이 없다** — golden에서 이 가지는 0번 돈다.
- **완충 장치**: 거짓 가격 5,600원은 분포 하단 극단값이라 `n ≥ 5`면 BM-05 Tukey가 `OUTLIER_LOWER`로 잡아 리뷰 큐로 보낸다. **표본이 적으면 안 돈다.**
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 수집으로 "가격이 `원`·콤마 없이 맨 숫자로만 표기되는 딜"의 빈도를 셀 수 있게 되면 — 0에 가까우면 `_BARE`를 제거하고 `가격없음 스킵`으로 보낸다(거짓 가격 > 놓침). 유의미하면 앞 토큰의 품사/형태로 가른다.
- **관련**: Q-63(후보 서열), Q-64(무표기 배송비).

### Q-64 갱신 (2026-07-10, 같은 날 검증)
- **"무표기가 관례인가"라는 질문 자체가 틀렸다.** 무표기 13건(가격 있는 것)을 세어 보니 원인이 셋이었다:
  - **제목 잘림 4건** → ✅ **해소**: `...`로 끝나고 배송 표기를 하나도 못 찾으면 `배송비미상`. 그중 `(772,800원/3,00...`은 **배송비 3,000원이 명시됐는데 잘려서** 조용히 0이 되고 있었다.
  - **디지털 재화 ~5건**(스팀·플레이스토어·CGV·던킨) → 배송 자체가 없다. **0이 진실이므로 태그하지 않는다.**
  - **실물인데 무표기 ~5건**(`닌텐도 스위치2 … 759,600원`) → 🔴 **여전히 모른다. 이것만 남았다.**
- **갱신된 사이트별 비율**: 번개 60% · 펨코 15% · 루리웹 **14.3%**(0%였다) · 뽐뿌 4.8%.
- **좁혀진 재개 트리거**(무엇이 참이 되어야 하는가): **실물과 디지털을 가를 수 있어야 한다.** 쇼핑몰명(`[스팀]`·`[플레이스토어]`)이 단서지만 목록이 유한하지 않다. 실 수집으로 쇼핑몰명 빈도를 얻으면 상위 N개로 디지털을 판정하고, 나머지 실물 무표기에 `배송비미상`을 단다. **지금 전부 태그하면 디지털 재화까지 "하한"이 되어 표식이 신호를 잃는다.**
- **교훈**: "표본이 없어 못 정한다"는 이 항목의 원래 서술이 틀렸다. 이미 가진 golden이 답의 3분의 2를 갖고 있었다 → `docs/99`.

### Q-63 · Q-65 갱신 (2026-07-10, golden으로 재검증)
Q-64의 전제가 golden으로 반박됐으므로 두 항목의 "실 표본이 없어 못 정한다"도 같은 방법으로 검증했다. **이번엔 주장이 옳다.**

| 질문 | golden 49개 제목 실측 | 판단 |
|---|---|---|
| Q-63: 제목에 `N만원` **문구**와 **별도 가격**이 함께 오는가 | **0건** | 서열을 뒤집을 근거가 없다. `35,000원 5만원 이상 무료배송`은 합성 케이스일 뿐 |
| Q-63: 가격이 **만원 표기로만** 오는가 | 1건(`(카할180만원대/무료)`) | 서열 1순위를 없애면 이 1건을 잃는다 |
| Q-65: 가격이 **`원`·콤마 없이 맨 숫자**로만 오는가 | **0건** | `_BARE` 가지는 golden에서 필요 없다. 그러나 `13490` 같은 표기가 실재하는지는 모른다 |

- **결론**: 둘 다 **실 수집 표본 없이는 못 정한다**는 원래 판단이 옳다. 다만 이제 그 근거가 "안 세어 봤다"가 아니라 **"49개 표본에서 0건"**이다.
- **재개 트리거(불변)**: 실 수집 1주 표본에서 각 케이스의 빈도를 센다. Q-65는 **0건이 이어지면 `_BARE`를 제거**한다(거짓 가격 > 놓침).

### Q-60 갱신 (2026-07-10) — 완화책에 **확인하는 법**을 붙였다
- **맥락**: `guard.sh`(PreToolUse)는 에이전트가 친 **명령 문자열만** 본다. `bash scripts/x.sh` 안의 `curl https://www.ppomppu.co.kr`은 아무것도 막지 못한다. 완화책은 "네트워크로 나가는 스크립트는 자기 자신에게도 opt-in 게이트를 건다"였는데 — **그게 지켜지는지 확인하는 법이 없었다.**
- **✅ 장치**: `scripts/check-network-optin.sh` — 네트워크 명령(`curl`·`wget`·`aws`) 줄에 **외부 URL 리터럴**이 있으면 그 파일은 opt-in 환경변수를 참조해야 한다. 면제는 `network-optin-allowlist.txt`에 사유와 함께(가드/훅의 계약 테스트는 URL이 **데이터**다). 계약 테스트 15건 → CI `lint`.
- **실측(2026-07-10)**: 스크립트 29개 중 외부 URL 없음 26 · opt-in 또는 선언 3. **위반 0건.**
- **🔴 남는 한계(적어 둔다)**: 이 게이트는 **필요조건**이다. 변수로 조립한 URL(`curl "$BASE/robots.txt"`)은 잡지 못한다 — `check-robots.sh`가 정확히 그렇다(사이트 목록을 `sites.py`에서 읽는다). 그 스크립트는 스스로 `ALLOW_REAL_ROBOTS`를 본다.
- **재개 트리거(불변)**: 훅이 스크립트 내부를 볼 수 있게 되거나(도구 표면 확장), 네트워크 호출을 한 함수로 모아 그 함수가 opt-in을 강제하면 이 게이트를 지운다.

## [✅ 해소 2026-07-16] Q-66. 수요축(demand axis) 기능이 통째로 죽어 있었다 — 이제 산다
> **①②③E 전부 해소.** ②(축 유형·읽기 경로)·①(파싱·저장·분포 분리·조회 400)·③(구매 필수·성적 분포)·E(값 미상 딜 승격 큐)를 순서대로 배선했다. `demandAxisMode`를 읽어 분기하는 코드가 생겼고, SPLIT이 실제로 표본을 나눈다. 아래 실측(죽은 것들의 사슬)은 이력으로 남긴다 — 각 항목에 해소 표시.
- **맥락**: `product.demand_axis_mode`(GROUPED/SPLIT)와 `AxisType.DEMAND`는 V1 스키마와 도메인에 있다. 확정본 §217도 "수요축 묶음/분리 = 사용자 선택"이라 적는다.
- **🔴 실측(2026-07-11) — 죽은 것들의 사슬**:
  1. **`AxisType.DEMAND`는 생산자도 소비처도 0**이다. `web/registration/buildCommand.ts`가 모든 축을 `axisType: 'PRICE' as const`로 보낸다. core는 그 값을 저장만 하고 읽는 코드가 없다.
  2. ~~**`product_axis`는 쓰기만 하는 테이블**이다. `RegisterProductUseCase`가 저장하고, `ProductAxisRepository.findByProductId`는 **호출자가 0**이다. `GetProductsUseCase.ProductSummary`에 축이 없어 web으로 돌아오지도 않는다.~~ **✅ 해소(2026-07-16)**: `GetProductsUseCase`가 `axes.findByProductId`를 부르고 `ProductSummary.axes`(유형·이름·값)로 낸다. 등록 화면의 제품 목록이 `용량(가격축) · 색상(수요축)`으로 그린다 — **수요축은 variant를 안 나눠 목록에 흔적이 없으므로 여기서 못 보면 확인할 길이 없다.** `repository-readers-allowlist`에서 면제 삭제(게이트가 면제 없이 통과). 스모크가 `"axes"`·`"axisType":"PRICE"` 왕복을 종단 검증.
  3. ~~**`DemandAxisMode.SPLIT`은 저장되지만 아무 동작도 바꾸지 않는다.**~~ **✅ 해소(2026-07-16, Q-66 ①)**: 분리가 실제로 표본을 나눈다. 순수 규칙 `DealSets.demandScope`(SPLIT이면 그 값의 딜만; **미상(null)은 어떤 요청값과도 같지 않아 자동 제외** = §41의 "미상 버킷은 기준가 제외"), 해석 정본 `VariantDemandScope`(모드를 읽어 좁힌다 — 기준가·신호등·알림이 각자 해석하면 한 화면이 서로 다른 사실을 말한다). **값 없이 물으면 400**(`BM_DEMAND_AXIS_VALUE_REQUIRED`) — 전체로 답하면 그게 묶음의 거짓말이고 화면상 구분이 없어 조용하다. 알림 경로도 **딜 자신의 값**으로 판정하고, 미상 딜은 어느 분포에도 못 대므로 발화하지 않는다. web: 판단 화면에 수요축 값 선택(값은 `ProductSummary.axes`에서). 등록 화면의 "아직 안 나눕니다" 문단은 seam대로 삭제 — 이제 거짓이다. 스모크 5-1i가 종단 검증(블랙 n=5·median 860,000 · 화이트/미상 미혼입 · 값 없으면 400).
  4. ~~**"SPLIT 필수"가 강제되지 않는다.**~~ **✅ 해소(2026-07-16, Q-66 ③)**: `RecordPurchaseUseCase`가 저장 전에 `demandScope.requireValueWhenSplit`로 막는다(SPLIT인데 값 없으면 400 `BM_DEMAND_AXIS_VALUE_REQUIRED`, 저장 전이라 500 아님). 그리고 as-of 스냅샷도 **산 색의 분포**에 대고 낸다(`freezeSnapshot`이 `demandScope.scope` 적용) — 화이트가 섞인 기준가에 블랙 구매를 대면 성적이 색과 무관해진다. web: 구매 패널이 판단 화면에서 고른 값을 그대로 받는다(자유 입력 제거 — 판단과 다른 색을 적을 위험). 산문(`RecordPurchaseCommand` javadoc)이 이제 실행되는 계약이 됐다. 테스트: `PurchaseEndpointTest`(값 없으면 400·저장 안 됨 / 값 주면 블랙 분포 median 860,000에 대고 +40,000) + 스모크 5-1i.
- **왜 위험한가**: 등록 화면이 "분리"를 고를 수 있게 그린다 — **없는 손잡이를 그리면 사람은 저장되는 줄 안다.** 그리고 모든 축이 가격축이라 색상 같은 축을 넣으면 **variant가 곱해져 표본이 쪼개진다**(n이 작아져 SPARSE). 사용자는 기준가가 왜 빈약한지 모른다.
- **✅ ② 해소(2026-07-16, 프론트 지휘 + core 소유권)**: 등록이 **축마다 유형(가격/수요)을 받고, variant는 가격축만으로** 생성된다. `AxisInput.type` 추가 → `buildCommand`가 실제 `axisType`을 보내고 `combine()`은 가격축만 곱한다(가격축 0이면 거부). `RegistrationPage`에 축별 유형 선택(기본 가격축). **core는 무수정** — `RegisterProductUseCase`가 이미 `axis.axisType()`을 그대로 저장하고 있었다(생산자만 없었다). 이로써 `AxisType.DEMAND`에 **생산자가 생겼고**, 기본값 GROUPED는 이제 **완전히 옳다**(수요축이 variant를 안 나누므로 모든 수요축 값이 한 기준가를 공유 = 묶음의 정의). 보드가 경고하던 "색상 축이 variant를 곱해 표본이 쪼개진다"도 해소 — 그 경고 문구·테스트를 지웠다(seam대로). 테스트: `buildCommand.test.ts`(수요축은 variant를 안 나눔·정의는 그대로 보냄·가격축 0 거부)·`RegistrationPage.test.tsx`(유형 선택 → 미리보기가 2개).
- **잠정값(남은 것)**: 화면이 **사실을 말한다** — "분리(SPLIT)는 아직 표본을 나누지 않습니다 — 값은 저장되지만 동작은 묶음과 같습니다"(①이 남아 여전히 참). 손잡이는 확정본에 있으므로 지우지 않는다. **①이 구현되면 그 문단을 지운다(seam).**
- **재개 트리거**(무엇이 참이 되어야 하는가): ① 기준가 표본 조립이 `demandAxisMode`를 읽어 SPLIT일 때 수요축 값별로 분포를 나눈다 — **딜의 수요축 값이 필요하다**: 확정본 §41이 답을 준다("분리 시 글에서 축 값 판별 불가한 딜은 **값 미상 버킷** → 기준가 계산 제외, 승격 큐에서 사람이 분류"). 즉 제목에서 수요축 값을 파싱(Matcher 확장) + `deal_event`에 그 값을 보존할 자리(스키마) + 미상 버킷의 큐 배선이 함께 필요하다 — **결정이 아니라 구현**(다세션). ~~② 등록이 축마다 유형을 받는다~~ ✅ 해소. ③ `demandAxisValue`가 SPLIT 제품에서 필수로 검증된다(`RecordPurchaseUseCase` — 이제 우리 소유).
- **관련**: `check-table-wiring.sh`는 `product_axis`를 통과시킨다(엔티티가 있으므로 "이름이 나타난다"). 그 게이트의 명시된 한계다 — 읽기만/쓰기만 하는 테이블은 사람이 찾는다.

## [해소 2026-07-21] Q-67. AL-03 후속 알림이 통째로 없다 — 로드맵은 "후속 자격 ✅ GREEN"이라 적었다
- **맥락**: `docs/12` AL-03은 후속 알림 3종을 요구한다 — `VERIFIED`(N개 사이트 교차검증) · `PRICE_CHANGED`(본문 가격 변화) · `ENDED`(품절·종료).
- **🔴 실측(2026-07-11)**: `FollowUpEvaluator.shouldSendFollowUp(FollowUpKind, boolean)`은 존재하고 단위 테스트도 GREEN이지만 **프로덕션 소비처가 0**이다. `FollowUpEvaluator`를 주입받는 곳도, `FollowUpKind.*`를 만드는 코드도 하나도 없다. 즉 **후속 알림은 한 번도 발화한 적이 없고, 앞으로도 발화하지 않는다.**
- **`docs/30` M1 표가 거짓이었다**: "AL 판정(트리거·게이트·**후속 자격**) ✅ GREEN (발송은 스텁)". 트리거·게이트는 배선됐으나 **후속 자격은 배선된 적이 없다.** 산문은 GREEN일 수 없다.
- **왜 위험한가**: 딜이 교차검증되거나 가격이 바뀌거나 종료돼도 사용자는 아무것도 못 듣는다. 특히 `ENDED` 후속은 "지금 사라"를 받고 달려간 사람에게 **"끝났다"고 말해 줄 유일한 경로**다. 그게 없으면 우리는 좋은 소식만 보내고 나쁜 소식은 침묵한다(절대 원칙 1·6).
- **잠정값**: 없음. 후속 알림은 발화하지 않는다. 텔레그램 어댑터가 스텁이라 지금은 로그조차 없다.
- **재개 트리거**(무엇이 참이 되어야 하는가): ① 딜의 상태·가격 변화가 **이벤트로 관찰**된다 ② 이미 알린 딜인지 알 수 있어야 한다(`alreadyAlerted`) ③ `AlertSender`가 후속 메시지를 낼 수 있어야 한다.
- **✅ 사실상 전부 해소됐다(발견: 2026-07-21)**: 위 세 트리거가 이미 참이었다 — 이 항목이 **세 번째 거짓 봉인**이다. ① `FollowUpAlertUseCase`가 `ReprocessDealPrices/Status`의 전이 id를 받아 `PipelineScheduler`가 매 틱 태운다(Q-57에서 그 발송 수 카운터까지 이었다). ② "알림 이력 저장소가 없다"는 **틀렸다** — `deal_alert` 테이블·`DealAlertEntity`·`DealAlertRepository.existsByDealEventIdAndKind`가 이미 있고 `EvaluateAlertOnDeal`이 FIRST를, `FollowUpAlertUseCase`가 kind별로 기록한다(멱등). ③ `AlertSender` 실 발송도 이제 있다(2026-07-21, `TelegramAlertSender` — Q-20 부분해소). **즉 후속 알림은 배선·이력·발송이 다 서 있고, 사용자가 `.env`에 토큰을 채우면 실제로 나간다.** 남은 건 봇 토큰(외부)뿐 — 코드 공백 없음.
- **관련**: Q-20(아웃바운드 발송 어댑터 — 해소) · Q-57(후속 발송 카운터) · Q-27 ③(품절 딜 오알림).

## [열림] Q-68. `deal_event.confidence`는 죽은 컬럼 — 매칭 신뢰도 자리인데 매칭이 신뢰도를 내지 않는다
- **맥락**: `deal_event.confidence numeric(3,2)`는 V1 스키마에 있다. `docs/02`·`docs/benchmark/02`가 스키마 정의에 나열한다(매칭 신뢰도 0.00~1.00을 담을 자리).
- **🔴 실측(2026-07-11)**: **채우는 프로덕션 코드 0, 읽는 코드 0.** `DealEventEntity`는 이 컬럼을 매핑하지 않고, BM-03 매칭(`Matcher`)은 CONFIRMED/CANDIDATE/UNKNOWN만 낼 뿐 **신뢰도 점수를 내지 않는다.** 컬럼은 항상 NULL이다.
- **`shipping`·`base_price`와 다르다**: 그 둘도 미매핑이지만 **의도적**이다 — 배송비는 `headline_price`에 합산되고(BM-02: 실결제가 + 배송비), `base_price`는 역산 금지(AC-2)라 항상 null이며, 스모크 5-1g가 `base_price=NULL`을 계약으로 못박는다. `confidence`만 **의도가 실현되지 않은 빈 자리**다.
- **왜 늦게 발견됐나**: 2026-07-10 컬럼 단위 소비처 0 감사가 `confidence`를 **놓쳤다** — 유일한 언급이 `DealEventEntity`의 javadoc("applied_conditions·confidence는 미매핑")이라 "이름이 나타난다"에 걸렸기 때문이다. "이름이 나타난다 ≠ 사용된다"의 정확한 사례(→ docs/99).
- **✅ 기계화(2026-07-21)**: 이 놓침을 다시는 안 하도록 `scripts/check-dead-columns.sh`(CI)를 만들었다 — DDL 컬럼이 프로덕션 코드(주석 제외)에 스네이크/카멜 어느 형태로도 안 나타나면 죽은 컬럼으로 차단한다. `confidence`는 `dead-columns-allowlist.txt`에 **이 Q-68을 인용**해 면제돼 있다 → Q-68이 닫히는 날(confidence가 실 소비처를 얻거나 컬럼이 삭제되면) 면제가 만료되고 게이트가 다시 묻는다. `check-table-wiring`은 테이블만 봐서 이 한 층 아래를 못 잡았다(그 게이트의 명시된 한계 → 이 게이트의 명세).
- **잠정값**: NULL 유지. 매칭은 이산 판정(CONFIRMED 등)으로 충분히 동작하며 confidence 없이도 파이프라인이 돈다. 컬럼은 스키마에만 있고, 그 사실을 게이트가 잠근다.
- **재개 트리거**(무엇이 참이 되어야 하는가): BM-03 매칭이 **연속 신뢰도 점수**를 내야 할 때(예: CANDIDATE를 점수순으로 정렬해 리뷰 큐 우선순위를 매기거나, 자동 승격 임계를 두려 할 때). 그때 `Matcher`가 점수를 내고 `IngestDealsUseCase`가 `deal_event.confidence`에 저장한다. 셋 다 core 기존 파일이라 조율. 컬럼 삭제(불필요하다고 판단 시)도 마이그레이션 = core 단독 소유.

## [열림] Q-69. USED-01 다중 TRIGGER 그룹 관계 = 그룹 간 AND (잠정)
- **맥락**: `docs/used/04` AC-3은 **단일** TRIGGER 그룹만 규정한다. 여러 TRIGGER 그룹을 두면 관계(AND/OR)가 미명시.
- **잠정값**: **그룹 간 AND** — 각 TRIGGER 그룹이 독립 필수 조건(그룹 내 OR, 그룹 간 AND). required와 대칭이고, "TRIGGER = 이게 있어야 알림"이라는 필수성 표현에 부합. seam = `UsedMatcher.evaluate`의 `triggerSatisfied` 한 곳(`allMatch` ↔ `anyMatch`). 테스트 `multipleTriggerGroupsAreAnded`가 잠금.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실사용에서 "여러 트리거 후보 중 하나라도 있으면 알림"이 필요해지면 `anyMatch`로 전환. 그 전까지 AND가 더 보수적(알림을 덜 낸다 = 오알림 적음)이나, USED는 재현율 우선이 아니라 정밀검색이라 AND가 정합적.

## [열림] Q-70. USED-02 후속 알림 = 승격 매물의 가격 "하락"만 (상승 제외, 잠정)
- **맥락**: `docs/used/04` AC-8은 "가격변동 → promoted면 후속 알림"이라 하나, 변동 방향(상승/하락)을 규정하지 않는다.
- **잠정값**: **하락만** 후속 알림(`currentPrice < previousPrice`). 상승은 배지·정렬만(스팸 방지·오알림 최소). 미승격 매물의 변동은 알림 안 함(AC-8). seam = `UsedAlertPolicy.shouldAlertOnPriceChange` 한 줄(`<` ↔ `!=`). 테스트 `followsUpOnPriceDropOnlyWhenPromoted`가 잠금.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실사용에서 "가격 올랐다"는 변동도 알고 싶어지면(예: 재고 소진 임박 신호) `!=`로 넓히거나 별도 저강도 알림 분리. 그전까지 하락만이 "더 좋은 딜" 관심사에 정합.

## [열림] Q-71. USED V3 스키마 bonus_groups 저장 = 그룹 행 + 키워드 text[] 배열 (잠정)
- **맥락**: `docs/used/02`가 "그룹 테이블 + 키워드 테이블 **또는** 배열 컬럼"으로 형태를 열어뒀다(JSONB만 금지).
- **잠정값(자율 확정, 되돌리기 쉬움)**: `used_search_bonus_group(id, used_search_id, mode, keywords text[])` — 그룹=행, 키워드=배열. V1 관례(`product_axis.allowed_values`·`alert_policy.exclude_keywords`가 이미 `text[]`)와 일관, 단순. **seam = 리포지토리 매핑 1곳**(도메인은 `BonusGroup(keywords, mode)`라 저장 형태를 모른다). 데이터 없는 지금은 형태 변경이 마이그레이션 하나라 되돌리기 쉽다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 그룹 내 개별 키워드에 메타(가중치·학습 출처 등)를 붙여야 하면 키워드 정규화 테이블(`used_search_bonus_keyword`)로 이전. 그전까지 배열이 충분.

## [열림] Q-79. CMP-01 온디맨드 비교 — web이 쿠팡 관측을 병기하나, 네이버 leg·확장 자체는 여전히 코드 밖
- **맥락**: `docs/13` CMP-01은 네이버 API 판매처가·쿠팡 확장가·최근 핫딜가를 한 화면에 병렬 표시하라고 한다. `GetLatestCoupangPriceUseCase`("CMP-01 재료")는 core에 있었지만 **소비처가 0**이었다 — REST(`GET /api/v1/coupang/variants/{id}/latest-price`)만 있고 web 아무도 안 불렀다(2026-07-23 실측, 응답 필드를 하나씩 grep해 찾음 — CLAUDE.md "API 필드 소비처 0" 감사 패턴).
- **✅ 해소(2026-07-23, 부분)**: `DecisionPage`가 판단 화면의 "근거·계기" 구간에 쿠팡 관측가를 병기한다(`coupangPriceLine`, 관측 없으면 "미확인 — 확장이 아직 연동되지 않았습니다"라고 정직하게 말한다·금액을 지어내지 않는다). `scripts/smoke.sh` 5-1j가 ingest(SEC-04 토큰 인증 401 포함)→latest-price 왕복을 종단 검증.
- **남은 것(둘 다 진짜 코드 밖)**:
  1. **네이버 판매처 leg** — `CurrentPriceProvider`가 여전히 스텁(Q-3, 키 미발급)이라 "다른 몰 최저가"를 별도로 보여줄 재료가 없다. 이미 `BenchmarkView.currentPrice`/`gapLine`이 그 자리를 대신 채우고 있어(같은 포트), 실은 CMP-01의 "네이버" 열은 판단 화면의 갭 표시와 **개념이 겹친다** — Q-3 해소 시 별도 열로 분리할지, 지금처럼 겹쳐 둘지는 그때 재확인.
  2. **쿠팡 크롬 확장 자체(코드)** — ingest 수신부는 끝났지만 송신부(manifest·content script·DOM 파서)가 없다. **지금 만들면 위험하다**: 실 쿠팡 상품 페이지의 DOM 셀렉터를 확인할 방법이 fixture 없이는 없고, 손으로 지어낸 셀렉터는 "우연히 옳은 코드"(docs/99 — 다음 쿠팡 개편에 조용히 틀려진다)가 되기 쉽다. 실 쿠팡 페이지 캡처는 사람이 브라우저로 방문해야 하는 일이라 자동화 정지조건과 같은 성격이다.
- **재개 트리거**(무엇이 참이 되어야 하는가): ① 네이버 키 발급(Q-3) — 그 순간 겹침 여부를 판단해 반영. ② 사용자가 실 쿠팡 상품 페이지 HTML(또는 스크린샷 기반 DOM 구조)을 fixture로 제공 — 그러면 파싱 로직을 TDD로(fixture golden) 만들고, manifest/content script는 그 위에 얹는다.
- **관련**: Q-3(네이버 키), Q-78(레이트리밋 잠정치).

## [열림] Q-80. D-6 루리웹 상세 fetch — 후보 선정은 배선됨, 실 fetch·파서는 fixture 대기
- **맥락**: `decisions-needed` D-6(2026-07-24 확정, ②안)은 "등록 제품 별칭이 걸리는 잘린 제목만 상세 fetch"다. 실제 fetch·파싱까지 해야 완성이지만, 루리웹 상세(view) 페이지의 실 HTML 구조를 담은 fixture가 없다 — 셀렉터를 손으로 지으면 CMP-02(Q-79 ②)와 같은 함정("우연히 옳은 코드")이고, 캡처 자체가 실 네트워크 호출이라 사람만 할 수 있다.
- **✅ 해소(2026-07-24, 부분)**: "무엇을 fetch해야 하는가"만 먼저 배선했다 — `collector/db/alias_source.py`(AliasSource, `alias_dictionary` 읽기, core 소유 테이블에 collector가 읽기 접근하는 기존 패턴과 동일) + `collector/pipeline/detail_fetch.py`(순수 함수 `needs_detail_fetch`: 가격 없음 + 잘림(`price.is_truncated`) + 등록 별칭이 **보이는(잘리지 않은) 부분**에 걸림 — 별칭이 잘린 뒷부분에만 있으면 추측하지 않고 놓침으로 둔다). `__main__.main()`이 매 사이클 후보를 골라 `detail_fetch_candidate` 이벤트로 로그하고 `cycle.detail_fetch_candidates` 카운터를 낸다(0도 낸다, OBS-02) — **아직 실제로 fetch하지는 않는다.**
- **남은 것**: 루리웹 상세 페이지 실 fetch(`fetch` 포트 재사용) + 그 HTML에서 가격을 복구하는 파서. `raw_deal_post.body_text`(Q-18에서 지목된 죽은 컬럼)가 저장 자리다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 사용자가 루리웹 상세 페이지(잘린 제목 글 하나) HTML을 fixture로 제공 — 그러면 파서를 TDD로(golden) 만들고, `detail_fetch_candidate` 로그를 실제 fetch 호출로 바꾼다.
- **관련**: `decisions-needed` D-6(해소·decision-log 참조), Q-18(2)(무료 딜, D-5로 별도 해소), Q-79(같은 모양의 CMP-02 확장 fixture 대기).

## [열림] Q-78. CMP-02 확장 ingest 레이트리밋 = 분당 30회 (잠정, 미검증)
- **맥락**: SEC-04는 "extension ingest: 고정 토큰 인증 + 스키마 검증 + 레이트 리밋"을 요구하나 구체 수치는 어디에도 없다(docs/31 위임 수치 표에 없음).
- **잠정값**: `CoupangObservationController.RATE_LIMIT_PER_MINUTE = 30`. 확장이 사용자가 쿠팡 페이지를 열 때마다(빠르면 몇 초 간격) 1건씩 보낸다고 가정 — 30/분이면 2초당 1건까지 견딘다. `FixedWindowRateLimiter`(순수, 시각 주입)로 격리(seam = 상수 1곳). 전역 카운터 하나(확장 1개·사용자 1인 규모라 토큰별로 나눌 이유가 아직 없다).
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 확장이 만들어져 실사용 트래픽이 관측되는 것 — 정상 사용에서 429가 나면 상수를 올리고, 오작동(무한루프 등)으로 과다 요청이 와도 안 막히면 낮춘다.

## [열림] Q-76. USED-04 평가기 URL 단계는 실 fetch하지 않는다 — 이미 폴링한 스냅샷만 본다 (잠정)
- **맥락**: `docs/used/04` AC-12는 "① URL fetch 1회 시도 → 실패 → ② 텍스트 → ③ 수동"을 요구한다. 실 fetch는 정지조건(외부 실호출)이라 코드가 그걸 대신할 수 없다. `docs/used/03`도 "번개 평가는 이미 폴링한 스냅샷을 재사용할 수 있어 추가 fetch가 불요할 수 있다(core가 확정)"고 열어뒀다.
- **잠정값**: URL 입력은 `listing`(V13 `url` 컬럼)에서 **같은 조건검색이 이미 관측한 매물**인지만 찾는다. 찾으면 그 title·price·url로 바로 구조화(①단 "성공"으로 취급). 못 찾으면 **①단 실패로 보고 바로 ②(TEXT 요청)로 폴백** — 진짜 HTTP 요청은 한 번도 나가지 않는다. seam = `EvaluateListingUseCase.findAlreadyPolled` 1곳.
- **한계**: 등록된 조건검색이 아직 그 매물을 못 본 경우(새로 올라온 매물·다른 검색어로 잡히는 매물)는 항상 TEXT 폴백으로 떨어진다. 이건 결함이 아니라 "실 fetch 금지"의 필연적 귀결이다 — 사용자가 붙여넣기 한 번 더 하는 비용으로 정지조건을 지킨다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 사용자가 URL 평가에서 TEXT 폴백이 너무 잦다고 느끼는 것. 그때도 실 fetch는 여전히 승인 대상이다 — 나아질 수 있는 건 스냅샷 커버리지(더 많은 검색 등록)이지 fetch 추가가 아니다.

## [열림] Q-77. USED-04 위험 신호의 "업자 레퍼토리 키워드" = 그 검색의 exclude_keywords 재사용 (잠정)
- **맥락**: `docs/used/04` AC-13·14는 "업자 레퍼토리 키워드"(예: "이민 급처")를 나열하라고 하지만, 그 어휘가 어디서 오는지(전용 사전? 사용자 입력?) 스키마·문서 어디에도 정해져 있지 않다. 지어낸 사기 키워드 목록을 하드코딩하면 그건 "판단"에 가까워진다(절대 원칙 2 위반 소지) — 사용자가 실제로 겪은 표현이 아니다.
- **잠정값**: 평가 대상 조건검색의 `used_search.exclude_keywords`를 그대로 재사용한다. 사용자가 이미 그 제품에 대해 "이런 표현이면 거른다"고 입력해 둔 값이라 새 어휘를 만드는 게 아니다. `cheapThresholdPct`(스냅샷 최저 대비 몇 %면 "과도하게 저렴" 플래그인가)는 `30`으로 고정(`EvaluateListingUseCase.CHEAP_THRESHOLD_PCT`) — `docs/31`의 `COLDSTART_JACKPOT_RATIO`(기준 없는 구간의 유일 알림 문턱)와 같은 논리로, 근거 없는 알림을 막으려면 높은 문턱이 정직하다.
- **한계**: exclude_keywords는 원래 "이 매물을 후보에서 뺀다"는 목적으로 입력된 것이라 "위험 신호로 나열한다"는 의미와 완전히 같지 않다 — 재사용은 새 어휘를 짓지 않기 위한 타협이지 정답은 아니다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 전용 업자 레퍼토리 사전이 필요하다는 결정이 나면(`decisions-needed`행) 별도 테이블/설정으로 분리. `cheapThresholdPct`는 실사용에서 오탐·누락이 관측되면 운영자 승인 수치로 전환(Q-37과 같은 패턴).

## [열림] Q-74. 번개 검색 1회 폴링에 가져올 매물 수 = 100 (잠정, 미검증)
- **맥락**: `market_spec`이 번개 검색 API에 `n=100`을 붙인다. 이 수가 작으면 **소실 오탐**이 난다 — 목록이 밀려서 사라진 매물을 "판매완료 추정"으로 읽고 알림까지 나간다(USED-03 AC-9). 크면 매 주기 트래픽·적재량이 는다.
- **잠정값**: `_LISTINGS_PER_POLL = 100`(`collector/src/collector/scheduler/market.py` 상수 1곳 = seam). `order=date`라 최신순이므로, 한 폴링 주기(기본 10분) 동안 100건 넘게 새로 올라오는 검색어면 뒤쪽이 잘린다.
- **⚠️ 이 값이 틀리면 조용히 틀린다**: 잘림은 예외도 로그도 남기지 않고 `disappeared`로만 나타난다. 실 폴링을 켜면 **한 배치의 매물 수가 상한에 붙어 있는지**(=100이면 잘렸을 가능성)를 먼저 본다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 폴링 1일치에서 배치 크기 분포가 나오는 것. 상한에 붙는 검색이 있으면 그 검색만 주기를 줄이거나 `n`을 키운다.

## [열림] Q-75. 사라졌던 매물의 재등장(revive)은 알리지 않는다 (잠정)
- **맥락**: `docs/used/04` AC-10은 "같은 listingId 재등장 = 신규 아님"(끌올 dedupe)이라고만 하고, **종착(SOLD) 상태였다가 다시 뜬 경우**를 규정하지 않는다. 판매완료 추정이 틀렸거나(예약 해제·숨김 해제) 재등록일 수 있다.
- **잠정값**: **알리지 않는다.** `FoldUsedListingsUseCase`가 같은 행을 되살리고(`revive`) 승격 표식은 보존하므로, 이후 가격하락·소실은 다시 알린다. seam = 되살리기 분기 1곳. 보수적인 쪽(놓침 < 오알림의 예외 — 이건 "같은 매물을 두 번 알리는" 스팸이라 오알림에 가깝다)을 택했다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 폴링에서 revive가 유의미하게 잦고(틱 리포트 `usedLifecycle[revived=..]`) 그중 "정말 다시 살 수 있게 된 매물"이 있는 것 — 그때 저강도 알림으로 분리한다. revived가 0에 가까우면 이 질문은 그대로 닫는다.

## [해소 2026-07-23] Q-72. M2 어댑터 미배선 — V3 스키마는 섰으나 생산자가 막혔다
- **맥락**: V3 used 스키마 7테이블 중 `used_search`·`used_search_bonus_group`은 등록 REST(`RegisterUsedSearchUseCase`·`UsedSearchController`)가 배선했다. 나머지 5테이블은 어댑터 미배선 → `check-table-wiring`이 잡는다(allowlist에 이 Q로 선언).
- **✅ 해소된 것(2026-07-22)** — 이 항목이 "막혔다"고 적어 둔 근거 중 **둘이 틀렸다**:
  - **`used_listing_observation`·`listing` 배선 완료.** 막힘 사유로 적힌 "collector엔 DB read 경로가 없다"는 **만들면 되는 일**이었다(실 네트워크와 무관). `used_search_source.py`(읽기)·`used_listing_sink.py`(insert-only 적재)·core `FoldUsedListingsUseCase`(diff→생애주기)·파이프라인 틱 배선까지 섰다. allowlist 2줄 삭제. `ListingDiff`(호출자 0)도 되살아났다.
  - 실 네트워크는 **폴링을 켜는 순간**에만 정지조건이다 — 코드·계약·테스트는 fixture와 Testcontainers로 전부 검증 가능하다. "정지조건 뒤"라고 적으면 다음 세션이 그걸 요구사항의 제약으로 읽는다(Q-50과 같은 거짓 봉인이었다).
- **✅ 완전 해소(2026-07-23)**: **robots 확인 완료**(사용자가 직접 실행 — 번개 ALLOW/robots 부재) → 마켓 폴링 루프 배선(`00651e0`) → **생애주기 알림 배선**(`544d5ff`, USED-03 AC-7·8·9·10) → **평가기 배선**(USED-04 AC-12·13·15) → **메모·축·비교표 배선**(USED-05 AC-16·17·18, core 쪽). V3 스키마 7테이블 전부 배선됐다: `used_search`·`used_search_bonus_group`(등록)·`used_listing_observation`·`listing`(접기)·`listing_note`·`comparison_axis`·`listing_axis_value`(메모/축/비교). `UsedMatcher`·`UsedAlertPolicy`·`UsedSearchSpec`·`UsedRiskSignals`·`UsedSearchBonusGroupRepository.findByUsedSearchId`·`UsedSearchRepository.findByProductId`가 전부 첫 소비자를 얻어 allowlist에서 지웠다. 원문 링크는 V13으로 파서→core까지 흐른다.
  - USED-04: `ListingExtractor`(v1 규칙, `RuleBasedListingExtractor`)·`PriceContextCalculator`(신규)·`UsedRiskSignals`를 `EvaluateListingUseCase`로 조립, `POST /api/v1/used-searches/{id}/evaluate`(Q-76·Q-77 잠정).
  - USED-05: `AddListingNoteUseCase`(AC-16, 구조 강제 없음) · `DefineComparisonAxesUseCase`(AC-17①, **추가 전용** — 축 삭제는 FK·데이터 유실 위험이라 별도 엔드포인트로 분리, 지금은 없음) · `PromoteAxisValueUseCase`(AC-17②, 재승격은 값 갱신) · `GetComparisonUseCase`(AC-18 데이터 — 빈칸은 **키 자체가 없다**, null 값이 아니다). REST 4종: `POST /listings/{id}/notes` · `PUT /products/{id}/comparison-axes` · `POST /listings/{id}/axis-values` · `GET /products/{id}/comparison`.
  - **비교표 렌더링(축 정렬·빈칸 체크리스트 표시)은 web 몫**이라 이 항목의 범위 밖 — core는 데이터만 낸다. web 착수는 사용자 지시 대기(별도 결정).
- **남은 것**: **번개 실 폴링 활성화**(`COLLECTOR_ALLOW_NETWORK=1`) — 정지조건이라 사람이 켠다. 코드·계약은 전부 섰다.
- **재개 트리거**(무엇이 참이 되어야 하는가): 실 폴링이 켜져 `listing` 행이 실제로 생기는 것.

## [해소 2026-07-22] Q-73. 텔레그램 인라인 버튼 피드백이 약하다 — 토스트는 있으나 놓치기 쉽고, 메시지·버튼은 안 변한다
- **✅ 완전 해소(2026-07-22, ①②③ + 실 스파이크 확인)**: 아래 세 지점을 전부 고쳤고, **재배포 후 실제 버튼 1회로 사용자가 동작을 확인**했다(모달이 뜨고 버튼이 사라짐). 페이크 검증 + core 전체 GREEN + **실 응답 검증 완료** — 이제 이 경로는 실 텔레그램으로 종단 입증됐다.
  - **①** `answerCallbackQuery`에 `show_alert=true` → 폴러가 **모달**로 답한다(일시 토스트→눌러 닫는 알림). `TelegramInboundPollerTest`가 `alert=true`로 부르는지 잠금. (`126cdeb`)
  - **②** 폴 주기 **10초→3초**. long-polling(`timeout=25`)은 파이프라인과 같은 @Scheduled 단일 스레드를 25초 블로킹해 틱을 밀므로 안 쓰고, 비블로킹 짧은 폴의 주기만 줄였다(1인용 저트래픽). (`126cdeb`)
  - **③** 처리 후 **메시지 편집**(`editMessageText` 신설 — 빈 inline_keyboard로 버튼 제거 + 원문 아래 결과). `route`가 `CallbackResult(editMessage, reply)`를 반환해 **상태가 바뀐 것만** 편집(권한실패·미지원·이미처리는 편집 안 함 — 남의 메시지·유효 버튼 보존). `CallbackUpdate`에 message 좌표 추가 + `parseCallbacks` 추출. (`c87f84f`)
- **남은 것**: 없음. (실 스파이크 2026-07-22 완료 — 사용자가 버튼을 눌러 모달·버튼 제거를 확인.)
- **맥락**: 실 텔레그램 검증(2026-07-22)에서 사용자가 딜 알림의 `[🔕무시]`를 눌렀는데 "된 건지 만 건지 반응이 없다"고 했다. **처리 자체는 정확히 됐다** — `deal_ignore`에 기록됐고 인바운드 경로(getUpdates→ReviewCallbackRouter→IgnoreDealUseCase)가 SEC-03 통과해 돌았다. 문제는 **누른 사람에게 돌아오는 피드백이 약하다**는 것.
- **🔴 실측(2026-07-22, 소스 grep 확인)**:
  - `answerCallbackQuery(callbackQueryId, reply)`는 **매 콜백마다 호출된다**(`TelegramInboundPoller:51`) — 한글 문구("무시했습니다 …")를 토스트로 띄운다. 죽은 코드 아님.
  - 그런데 ① **`show_alert=true`를 안 보낸다**(`HttpTelegramApi.answerCallbackQuery`가 `callback_query_id`+`text`만) → 화면 상단 **일시 토스트**라 놓치기 쉽다. ② 폴러가 `getUpdates`를 `timeout=0`·10초 주기로 쳐서 콜백이 최대 ~10초 늦게 응답되고, 텔레그램이 "query is too old"로 거절하면 토스트가 아예 안 뜬다(catch에서 삼킴). ③ **`editMessageText`/`editMessageReplyMarkup`이 아예 없다**(core 전수 grep 0) → 처리 후에도 원 메시지·버튼이 그대로 남아 **시각적으로 "안 된 것처럼" 보인다.**
- **잠정값(현재)**: 약한 일시 토스트만. 처리는 정확하나 사용자가 그걸 모른다.
- **고칠 수 있는 지점(비용 오름차순, 손잡이 격리)**:
  - **가장 싼 것**: `answerCallbackQuery`에 `show_alert=true` 추가 → 일시 토스트 대신 **모달**(사용자가 눌러 닫아야 함, 놓치기 어려움). `HttpTelegramApi` 한 줄 + 시그니처에 flag. **가장 먼저 할 것.**
  - **폴링 지연**: `getUpdates`에 long-polling(`timeout=25`)을 주면 콜백을 훨씬 빨리 받아 stale 거절이 준다. `HttpTelegramApi.getUpdates`의 `timeout=0`을 바꾸고 HttpClient read timeout을 그 이상으로.
  - **메시지 변경(가장 값진, 배선 필요)**: 처리 후 `editMessageReplyMarkup`으로 버튼 제거 + `editMessageText`로 "✅ 무시됨" 상태 표기. **선행**: `CallbackUpdate` record에 `messageId`(+원 메시지 chat_id) 추가 + `HttpTelegramApi.parseCallbacks`가 `callback_query.message.message_id`를 뽑아야 한다(현재 미추출). `TelegramInboundApi`/`HttpTelegramApi`에 editMessage 추가. 실 응답이라 수동 스파이크 검증.
- **재개 트리거(이미 참)**: 실 사용에서 피드백 부족이 확인됐다(2026-07-22). 우선순위 낮음(처리는 정확) — 하지만 `show_alert=true` 한 줄은 저비용·고효과라 다음 텔레그램 증분에 먼저 넣는다. core 기존 파일(HttpTelegramApi·TelegramInboundApi·Poller)이라 세션 소유권 조율.
