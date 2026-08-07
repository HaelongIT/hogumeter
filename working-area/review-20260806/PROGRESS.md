# 코드리뷰 20260806 — 진행상황

> 1차 = **정적 검증**(빌드·실행 없이 읽기로 판정). 범위는 **발견까지** — 수정은 2차.
> 계획 원본: `C:\Users\syj\.claude\plans\smooth-questing-oasis.md`
> 리뷰어 = `Agent`(general-purpose, **model=sonnet**, effort=세션 상속 high). **한 배치 = 2명 동시.**
> 배치가 끝나야 다음 배치를 띄운다. 중단되면 아래 「재개 지점」부터 다시 시작한다.

## 재개 지점
> **✅ 1차 리뷰 전 단계 완료.** 다음은 **2차(수정)** — `00-summary.md`의 「다음(2차)을 위한 메모」부터.
>
> **반박 검증 최종: CONFIRMED 7 / DOWNGRADED 3 / REFUTED 0** (기각 0이지만 3건이 강등돼 검증이 실효했다)
> (2026-08-06 15:05 세션 한도로 배치 D가 한 번 중단됐다가 재실행해 완료. 리뷰어 11명 산출물 전부 확보.)

## 배치
| 배치 | 리뷰어 | 범위 | 상태 |
|---|---|---|---|
| A | A1 / A2 / A3 | core 순수 도메인 · 유스케이스 · 영속성+Flyway | ✅ 완료 (3명, 재편 전) |
| B-1 | B1 / B2 | REST 어댑터 · 텔레그램+스케줄러 | ✅ 완료 |
| B-2 | B3 / C1 | 보안 표면 횡단 · collector 파서+파이프라인 | ✅ 완료 |
| C | C2 / C3 | collector 수집런타임 · web | ✅ 완료 |
| D | D1 / D2 | 모듈 간 계약 드리프트 · 스크립트+인프라+CI | ✅ 완료 (1회 중단 후 재실행) |
| E-1 | A1-01 / A2-01 | 반박 검증 | ✅ 완료 (둘 다 CONFIRMED) |
| E-2 | B2-01 / C1-01 | 반박 검증 | ✅ 완료 (둘 다 DOWNGRADED) |
| E-3 | C2-01 / C3-01 | 반박 검증 | ✅ 완료 (둘 다 CONFIRMED) |
| E-4 | C3-02 / C3-03 | 반박 검증 | ✅ 완료 (둘 다 CONFIRMED) |
| E-5 | D2-01 / D2-02 | 반박 검증 | ✅ 완료 (D2-01 DOWNGRADED · D2-02 CONFIRMED) |
| F | F1 / F2 + 메인 | 통합 → 10/20/30 + 00-summary | 🔄 진행중 (10/20/30 완료, 00-summary 작성 중) |

### 통합 결과
| 문서 | 항목 | High | Medium | Low | Info |
|---|---|---|---|---|---|
| `10-backend.md` (BE-01~) | 30 | 3 | 16 | 8 | 3 |
| `20-frontend.md` (FE-01~) | 4 | 3 | 1 | 0 | 0 |
| `30-cross-cutting.md` (X-01~) | 10 | 1 | 5 | 3 | 1 |
| **합계** | **44** | **7** | **22** | **11** | **4** |

> High 7건 = 반박 검증 CONFIRMED 7건과 일치. 병합된 중복 0건(8개 리뷰어가 서로 다른 계층을 봐 진짜 중복이 없었다).
> `10-backend.md`에는 리뷰어들이 스스로 기각한 항목이 **31개 주제**로 정리돼 있다 — 다음 리뷰가 중복 조사하지 않게 하는 것이 목적.

### 반박 검증 최종 집계
| 판정 | 건수 | 항목 |
|---|---|---|
| CONFIRMED (High 유지) | 7 | A1-01, A2-01, C2-01, C3-01, C3-02, C3-03, D2-02 |
| DOWNGRADED (High→Medium) | 3 | B2-01, C1-01, D2-01 |
| REFUTED | 0 | — |

> 기각 0건이지만 **강등 3건**이 나왔고, 강등 근거가 전부 "하류 방어 장치의 실재"(REST 완충 경로 / `OutlierDetector` Tukey IQR / 현재 살아 있는 위반 인스턴스 0건)라 검증이 실효했다.
> 검증자가 **원 발견을 보강한 사례**도 있다 — A1-01(더 직접적인 도달 경로 추가 확인), C2-01(원 발견이 놓친 더 급한 실패 경로 발견), C3-01(메커니즘 설명은 정정하되 결론 유지).

상태 범례: ⬜ 대기 / 🔄 진행중 / ✅ 완료 / ⚠️ 실패·부분

## 발견 집계 (리뷰 단계 최종)
| 리뷰어 | 산출물 | High | Medium | Low | Info | 상태 |
|---|---|---|---|---|---|---|
| A1 | `raw/A1-domain.md` | 1 | 1 | 0 | 2 | ✅ |
| A2 | `raw/A2-usecase.md` | 1 | 3 | 1 | 0 | ✅ |
| A3 | `raw/A3-persistence.md` | 0 | 2 | 0 | 3 | ✅ |
| B1 | `raw/B1-web.md` | 0 | 4 | 4 | 3 | ✅ |
| B2 | `raw/B2-adapter.md` | 1 | 0 | 2 | 1 | ✅ |
| B3 | `raw/B3-security.md` | 0 | 1 | 2 | 2 | ✅ |
| C1 | `raw/C1-parser.md` | 1 | 1 | 0 | 1 | ✅ |
| C2 | `raw/C2-collector-runtime.md` | 1 | 2 | 0 | 1 | ✅ |
| C3 | `raw/C3-web.md` | 3 | 1 | 0 | 2 | ✅ |
| D1 | `raw/D1-contract.md` | 0 | 3 | 2 | 2 | ✅ |
| D2 | `raw/D2-infra.md` | 2 | 1 | 1 | 0 | ✅ |
| **누계** | | **10** | **19** | **12** | **17** | |

> ⚠️ 통합(F) 시 정정할 것: `raw/B1-web.md:3`과 `raw/D2-infra.md:3`의 요약 헤더가 템플릿 문구(`High N / Medium N ...`) 그대로 남아 있다. 실제 건수는 위 표(리뷰어 최종 응답 기준)를 따른다.

## 반박 검증 대기열 (배치 E) — High 10건
> 검증자는 **기각이 기본값**이며 원 발견자와 다른 에이전트다. 산출물은 `rebuttal/<항목ID>.md`.

| 항목ID | 제목 | 배치 | 검증 상태 | 판정 |
|---|---|---|---|---|
| A1-01 | 별칭 사전 substring 다중 히트 시 매칭 결과가 JVM 실행마다 달라질 수 있다 | E-1 | ✅ | **CONFIRMED** — `Map.copyOf`(JDK `MapN`, SALT32L) 순회 순서가 JVM 재기동마다 달라짐을 생성 경로까지 추적 확인. DB 유니크는 `(product_id, alias)`만 막아 다중 productId 히트를 못 막고, `RegisterProductUseCase`의 수동 별칭 입력이 substring 검증 없이 열려 있어 **원 발견보다 더 직접적인 도달 경로**까지 확인. CANDIDATE 강등 없이 바로 확정되므로 "애매하면 후보" 원칙에 의한 하향도 미적용 |
| A2-01 | 딜 병합 시 도메인이 계산한 수요축 값이 엔티티에 반영되지 않는다 | E-1 | ✅ | **CONFIRMED** — 인용·시그니처·우회 갱신 경로 부재를 전부 원문 대조. `DealMergePolicy.sameTarget`이 축 값을 안 보고 variantId만 보므로 **서로 다른 축 값의 딜이 실제로 병합될 수 있어** 표본 오염이 도달 가능 |
| B2-01 | `HttpTelegramApi` 인바운드 경로가 HTTP 상태를 전혀 검사하지 않아 실패가 완전히 침묵 | E-2 | ✅ | **DOWNGRADED(High→Medium)** — 코드 사실관계는 전부 정확(상태 미검사, `parseCallbacks`가 401/403을 빈 목록으로 흡수, 인바운드 폴러가 `PipelineHealthMonitor`/`AdminNotifier`에 미연결, 테스트 전무). 그러나 영향이 핵심 파이프라인이 아닌 **부차 기능(승격/기각/무시)**에 국한되고, `ReviewQueueController`가 텔레그램을 안 거치는 **REST 완충 경로**를 제공하며, 가장 그럴듯한 트리거(토큰 무효화)는 아웃바운드도 죽여 `log.error` 흔적을 남긴다 |
| C1-01 | `_BARE` 폴백이 백트래킹으로 자기 가드를 우회해 5자리+ 모델번호를 거짓 가격으로 읽음 | E-2 | ✅ | **DOWNGRADED(High→Medium)** — 재현은 정확히 일치(`i5-14600K CPU 특가` → `headline_price=1460` 등 5건 전부). 그러나 같은 variant에 딜 5건 이상이면 core `OutlierDetector`의 Tukey IQR이 걸러 리뷰 큐(`OUTLIER_LOWER`)로 보내고, **`docs/91` Q-65가 같은 근본 원인·같은 완충·같은 잔여 갭을 이미 열어 수용**해 둔 상태이며, golden fixture 118건 전수에 유사 실제 제목이 **0건**이라 도달 가능성 실측 근거가 없다 |
| C2-01 | DB 쓰기 실패 후 rollback 부재로 커넥션 영구 오염, 연쇄 실패 | E-3 | ✅ | **CONFIRMED** — `connect_from_env()`가 `autocommit` 없이(psycopg 3 기본 트랜잭션 모드) 열고 **다섯 싱크/소스가 커넥션 하나를 공유**하며 저장소 전체에 `rollback()` 호출이 **0건**(Grep). CHECK 위반 트리거는 `test_raw_deal_sink.py::test_status_outside_the_contract_is_rejected_by_the_database`로 이미 실증됨. 검증자가 **원 발견이 짚지 않은 더 급한 경로**(`all_searches()`/`all_aliases()`가 try/except 없이 호출)까지 추가 발견 |
| C3-01 | variant를 GROUPED→SPLIT(축 미선택)으로 바꾸면 이전 variant 판단 요약이 안 지워짐 | E-3 | ✅ | **CONFIRMED** — 조회 `useEffect`가 121행 조기 `return`으로 `setLoaded(null)`을 건너뛰고, 렌더된 판단 요약 섹션(234-336행)에 **제품/variant 이름이 전혀 없어** 옛 값이 새 variant 것처럼 보인다. 막는 테스트 없음. (원 발견의 "state bail-out" 설명은 과잉 — 값이 애초에 안 바뀌므로 무관 — 이나 **핵심 결함과 High 판정은 유지**) |
| C3-02 | `PurchasePanel`이 variant 전환 응답 레이스를 막지 않음 | E-4 | ✅ | **CONFIRMED** — `PurchasePanel.tsx:74-78`의 `useEffect`는 **cleanup 자체가 없다**. 다른 5개 파일(`AlertPolicyPanel`·`WatchPage`·`UsedSearchPage`·`ReviewQueuePage`·`PriorityPage`)이 `let live` 가드를 쓰는 것을 직접 확인. 부모 `DecisionPage`/`App` 어디에도 `key` prop이 없어 재마운트 방어도 없고, 구매 목록에 variant 식별 정보가 없어 **사용자가 못 알아챈다**. 기존 테스트는 순차 시나리오만 검증 |
| C3-03 | `UsedComparisonPage`도 같은 패턴의 응답 레이스 | E-4 | ✅ | **CONFIRMED** — `UsedComparisonPage.tsx:130-142`에 취소·세대 가드 전무. `UsedPage.tsx`가 `UsedEvaluatePage`에만 `key`를 주고 이 컴포넌트엔 안 준다. 게다가 product 전환이 **부모가 아닌 컴포넌트 내부 `<select>` state**로 일어나 리마운트 방어 자체가 성립 불가 |
| D2-01 | `open_question()`이 `[해소]`된 Q를 열린 것으로 오판 (게이트 3종) | E-5 | ✅ | **DOWNGRADED(High→Medium)** — 정규식 결함(닫힌 `## [해소 …] Q-N`을 열림으로 오판)은 실측 재현으로 확인됐고 `check-dead-columns.sh`만 고쳤다는 비교도 사실. 그러나 세 allowlist 전수 대조 결과 **현재 살아 있는 항목은 `price_history/Q-3` 하나뿐이고 Q-3는 여전히 `[열림]`** — 지금 이 순간 거짓 초록을 내는 인스턴스가 0건 |
| D2-02 | `check-network-optin.sh`가 주석에만 있는 opt-in 변수명을 게이트로 오인 | E-5 | ✅ | **CONFIRMED** — 임시 디렉토리 재현으로 **실제 거짓 초록 확인**(opt-in 변수명이 주석에만 있고 `curl`은 게이트 없이 실행되는 스크립트가 `NETWORK OPTIN OK`로 통과). `guard.sh`는 자기 헤더가 명시하듯 `bash scripts/x.sh` **내부 호출을 애초에 못 봐** 두 번째 방어선이 되지 못함(재현으로 exit 0 확인). `check-network-optin.test.sh`도 이 방향을 시험하지 않음 |

## 리뷰어 자가 총평 (요약, 상세는 raw 파일)
- **A1**: 별칭 매칭이 `Map` 반복 순서에 의존해 재기동마다 다른 제품으로 확정될 수 있음(High). 0원 구매 기록 시 관찰 문맥 계산이 0으로 나누기(Medium). 상태기계 우회 의심 1건은 엔티티 계층 안전망 확인 후 **스스로 기각**.
- **A2**: `DealMergePolicy.merge()`가 계산한 `demandAxisValue`가 `DealEventEntity.applyMerge` 호출에서 누락돼 영구 소실(High) — SPLIT 제품 표본 오염 방지를 병합 경로에서만 무력화. 트랜잭션 경계 2건·N+1 1건(Medium).
- **A3**: 타입·enum·V↔R 대칭성·네이티브 SQL 인젝션·로케일 정렬은 견고. 정렬 없는 조회로 대표 원문 링크 재현 불가(Medium), `RawDealPostUpserter`가 빈 등록도 호출자도 없음(Medium).
- **B1**: `@RequestBody` record의 참조타입 필드 누락(null)이 도메인·영속까지 흘러 500이 나는 곳 3건(Medium). **계획 전제 2개를 실측 정정** — 쿠팡 `GET latest-price` 인증 비대칭은 "GET엔 앱 레벨 인증이 없는 일관된 설계"라 기각, `GlobalSettingsController` "테스트 0건" 전제도 부정확.
- **B2**: `HttpTelegramApi` 인바운드(`getUpdates`/`answerCallbackQuery`/`editMessageText`)가 HTTP 상태 미검사 → 토큰 무효화·차단 시 버튼 채널이 **로그 한 줄 없이 영구 정지**(High).
- **B3**: 시크릿 취급·헬스 노출·확장 ingest 인증은 원칙대로 안전. 진짜 구멍은 **절차 갭** — `--profile public` 공개 노출과 `preflight.sh prod` 검증이 프로그램적으로 묶여 있지 않음(Medium).
- **C1**: `price.py` "원 없는 숫자" 폴백이 **정규식 백트래킹으로 자기 가드를 우회** — `14600K`·`20000mAh`가 거짓 가격이 됨. **실제 재현으로 확인**(High).
- **C2**: DB 쓰기 실패 시 psycopg `rollback`이 **어디에도 없어** 공유 커넥션이 오염되고 연쇄 실패 후 프로세스 조기 종료(High). `drift.py`가 `priced_count`를 안 봐 "가격 전무" 드리프트 알림이 매 사이클 반복(Medium, **모듈 실행으로 재현**). robots.txt 5xx를 "전체 허용"으로 처리(Medium).
- **C3**: 응답 레이스·상태 초기화 누락 3건(High) — **같은 저장소의 다른 5개 파일은 `live` 가드 패턴을 쓴다**. `present.ts:97`이 `shared/kst.ts`를 우회해 UTC/KST 하루 밀림 재현(Medium). **`types.ts`↔core 20개 타입 전수 대조에서 불일치 없음.**
- **D1**: core↔collector 컬럼·타입·enum은 정합. 다만 `check-dead-columns.sh`가 "생산자가 이름을 안다"와 "소비자가 읽는다"를 구별 못해 `raw_deal_post.reaction_score`(docs/90 확정본 요구 필드)가 소비처 0인데 GREEN(Medium, **실행 확인**). `docs/91` Q-3의 죽은 재개 트리거를 allowlist 2곳이 여전히 인용(Low).
- **D2**: 게이트 철학은 대체로 코드에 반영돼 있으나 **나중에 복제된 게이트에서 규율이 누락돼 자기모순** — `open_question()`이 `[해소]`된 Q를 열림으로 오판(게이트 3종, High), `check-network-optin.sh`가 주석의 변수명을 게이트로 오인해 **실 네트워크 최후 방어선이 주석 한 줄로 뚫림**(High).

## 메인 세션 인용 대조
> 검증자가 원문 재확인을 수행하므로 메인은 **CONFIRMED로 살아남은 항목**에 대해서만 최종 대조한다(통합 F 단계).

**✅ High 7건 전부 대조 완료 — 전부 원문과 일치.** 상세 표는 `00-summary.md`「메인 세션 인용 대조」.

부수 확인 2건:
- `UsedComparisonPage.tsx:141`에 `eslint-disable-next-line react-hooks/exhaustive-deps`가 있는데 **저장소에 eslint가 없다** — 억제 주석이 "검토했다"는 거짓 표식으로 남아 있다.
- `DealEventEntity.applyMerge`는 `demandAxisValue`를 **받을 파라미터 자체가 없다** — 호출자가 실수로 빠뜨린 게 아니라 구조적으로 넘길 수 없다(BE-01 수리 시 시그니처 변경이 필요하다는 뜻).

## loose-end 라우팅 (CLAUDE.md 프로토콜)
| 성격 | 어디에 | 상태 |
|---|---|---|
| 무중단 중 사용자가 알아야 할 것 | `working-area/progress-log.md` | ✅ 2026-08-07 (1) |
| 재사용 교훈 | `docs/99-lessons.md` | ✅ 2026-08-07 (2건) — "정본 패턴을 나중 코드가 안 따른다" / "반박 검증은 기각 0이어도 실효한다" |
| 사람이 정해야 할 것 | `working-area/decisions-needed.md` | — 없음. 발견 44건은 전부 되돌릴 수 있는 코드 수정이라 2차 자율 범위 |
| 기술 보류 | `docs/91-open-questions.md` | — 등록 안 함. 발견은 이 리뷰 문서가 정본이고, 91에 사본을 만들면 드리프트한다(`docs/91` Q-65는 이미 있던 항목이라 강등 근거로 인용만 함) |
