# 코드리뷰 20260806 — 00. 요약

> **1차 = 정적 검증**(빌드·실행 없이 읽기로 판정 가능한 것). 1차 범위는 발견까지 — **2차(수정)는
> 44건 전부 처리 완료**(2026-08-07: High 7 + Medium 22 + Low/Info 15).
> 상세: `10-backend.md`(BE) · `20-frontend.md`(FE) · `30-cross-cutting.md`(X)
> 원시 산출물: `raw/` 11개 · 반박 검증: `rebuttal/` 10개 · 진행 기록: `PROGRESS.md`

## 방법
- 리뷰어 **11명**(`Agent` general-purpose, model=sonnet, effort=high)이 모듈을 나눠 정적 검토. **한 배치 2명**씩 순차 실행(중단 시 완료분 보존).
- 리뷰어에게 CLAUDE.md의 **절대 원칙·축적된 규칙을 프롬프트로 직접 주입**했다(서브에이전트는 CLAUDE.md를 자동 로드하지 않는다).
- **오탐 억제 규율**: 인용(`파일:줄`) 없는 발견 폐기 / 실패 시나리오를 못 쓰면 Info 강등 / **올리기 전에 이미 막는 장치(테스트·게이트·DB 제약·상위 호출자)를 찾아보고 결과를 적을 것**.
- **High 전 항목에 반박 검증**을 붙였다 — 원 발견자와 다른 에이전트가 **기각을 기본값**으로 재조사.
- 검토 대상 규모: core main 14,407줄 / collector 7,329줄 / web 7,559줄 / scripts 4,622줄.

## 상태 마커 범례
- **✅수정완료** (커밋해시) — 고쳤고 커밋됨
- **⏸보류** — 의도적으로 안 함(사유·재개 트리거 → `docs/91-open-questions.md`)
- **◐부분반영** — 핵심만 처리, 잔여는 backlog
- **📄문서화** — 코드 아닌 문서/운영으로 처리
- **❌미해결** — 아직 안 함

## 심각도
High(즉시) · Medium · Low · Info

## 이번 리뷰 집계
| 심각도 | 건수 | 항목 |
|---|---|---|
| High | 7 | BE-01 · BE-02 · BE-03 · FE-01 · FE-02 · FE-03 · X-01 |
| Medium | 22 | BE-04~19 (16) · FE-04 (1) · X-02~06 (5) |
| Low/Info | 15 | BE-20~30 (11) · X-07~10 (4) |
| **합계** | **44** | |

## 처리 현황
**44건 전부 처리 완료(2026-08-07).** 대부분(37건)은 ✅수정완료(코드+회귀 테스트, Red→Green).
나머지는 성격에 맞게 처리했다: BE-07(재검토 결과 실결함 아님) · X-03(정책 결정 필요 —
`docs/91` Q-89 + `decisions-needed` D-10 등록) · X-06(**추가 후속(2026-08-07) — Q-90 collector·core
잔여도 마저 해소, 44건 전부 코드 처리 완료**) · X-10(시도했으나 게이트 로직상 선언 불가 — X-03과 같은 근본 원인, 문서화만) ·
BE-25·BE-27(원 리뷰 권고 자체가 "지금 조치 불필요" — 그대로 보류). 상세는 각 문서의 상태
마커 참고. 검증: core 832+ · collector 361 · web 289 테스트 + `npm run lint`/`build` +
셸 계약 테스트 18개 + `scripts/smoke.sh`(비루트 컨테이너 포함 종단 13단계) 전부 GREEN.

## High 7건 (반박 검증 통과) — 전부 2차에서 TDD(Red→Green)로 수정 완료
| ID | 제목 | 소유 | 무엇이 깨지는가 | 상태 |
|---|---|---|---|---|
| **BE-01** | 딜 병합 시 도메인이 계산한 수요축 값이 엔티티에 반영되지 않는다 | deal-merge | `DealMergePolicy.merge()`가 낸 `demandAxisValue`가 `applyMerge` 호출에서 버려진다. `sameTarget`이 축을 안 보고 variantId만 봐 **다른 사양의 딜이 병합**되고, SPLIT 제품의 표본 오염 방지가 병합 경로에서만 무력화된다 | ✅수정완료(0a9b9bb) |
| **BE-02** | collector DB 쓰기 실패 후 rollback 부재로 커넥션이 영구 오염 | collector-db | `connect_from_env()`가 autocommit 없이 열고 **다섯 싱크/소스가 커넥션 하나를 공유**하는데 저장소 전체에 `rollback()`이 0건. 단발 쓰기 오류가 이후 모든 저장을 연쇄 실패시키고 프로세스가 조기 종료된다 | ✅수정완료(c67a579) |
| **BE-03** | 별칭 사전 substring 다중 히트 시 매칭이 JVM 실행마다 달라진다 | matching | `Map.copyOf`(JDK `MapN`, SALT32L)의 순회 순서가 재기동마다 바뀐다. DB 유니크는 `(product_id, alias)`만 막아 **다중 productId 히트**를 못 막고, 수동 별칭 입력이 substring 검증 없이 열려 있다. CANDIDATE 강등 없이 바로 확정된다 | ✅수정완료(9f5f951) |
| **FE-01** | GROUPED→SPLIT(축 미선택) 전환 시 이전 variant 판단 요약이 안 지워진다 | decision | 조회 `useEffect`가 121행 조기 `return`으로 124행 `setLoaded(null)`을 건너뛴다. 판단 요약 섹션에 **제품/variant 이름이 없어** 옛 신호등·기준가·갭이 새 variant 것처럼 읽힌다 | ✅수정완료(e730fec) |
| **FE-02** | `PurchasePanel`이 variant 전환 응답 레이스를 막지 않는다 | purchase | `useEffect`에 **cleanup 자체가 없다**(형제 5개 파일은 `let live` 가드를 쓴다). 구매 목록에 variant 식별 정보가 없어 "이미 샀다/안 샀다"를 오인할 수 있다 | ✅수정완료(767c12d) |
| **FE-03** | `UsedComparisonPage`도 같은 패턴의 응답 레이스 | used | 취소·세대 가드 전무. product 전환이 **컴포넌트 내부 `<select>` state**로 일어나 `key` 재마운트 방어가 성립조차 못 한다 | ✅수정완료(4c2d966) |
| **X-01** | `check-network-optin.sh`가 주석에만 있는 opt-in 변수명을 게이트로 오인 | — | 실 네트워크 호출을 막는 정적 게이트가 **주석 한 줄로 뚫린다**(재현 확인). `guard.sh`는 자기 헤더가 명시하듯 `bash scripts/x.sh` 내부 호출을 못 봐 두 번째 방어선이 되지 못한다 | ✅수정완료(0638e82) |

## 반박 검증 결과
| 판정 | 건수 | 항목 |
|---|---|---|
| CONFIRMED | 7 | BE-01 · BE-02 · BE-03 · FE-01 · FE-02 · FE-03 · X-01 |
| DOWNGRADED (High→Medium) | 3 | 텔레그램 인바운드 상태 미검사 · 가격 파싱 백트래킹 · 게이트 `open_question()` 오판(X-02) |
| REFUTED | 0 | — |

**기각률 0%지만 강등 30%.** 강등 근거가 전부 *하류 방어 장치의 실재*였다는 점이 중요하다 —
① 텔레그램 인바운드가 죽어도 `ReviewQueueController`의 REST 완충 경로가 있다, ② 거짓 가격은 표본 5건 이상이면 core `OutlierDetector`의 Tukey IQR이 리뷰 큐로 보낸다(그리고 `docs/91` Q-65가 같은 근본 원인을 이미 열어 수용해 뒀다), ③ 게이트 정규식 결함은 사실이나 **현재 살아 있는 위반 인스턴스가 0건**이다.
검증자가 **원 발견을 보강한 사례**도 셋 있다 — BE-03(더 직접적인 도달 경로 추가 확인), BE-02(원 발견이 놓친 더 급한 실패 경로 발견), FE-01(메커니즘 설명은 정정하되 결론 유지).

## 메인 세션 인용 대조
반박 검증과 별개로, **High 7건 전부**의 인용을 메인이 원문에서 직접 확인했다.

| 항목 | 확인한 것 | 결과 |
|---|---|---|
| BE-01 | `DealEventEntity.java:195-196` 시그니처 · `IngestDealsUseCase.java:155-156` 호출부 · `DealMergePolicy.java:69` | ✅ 일치 — `applyMerge(long, long, long, long, boolean, DealStatus, Instant, Instant)`에 **`demandAxisValue` 파라미터가 아예 없다.** 정책은 `mergeDemandAxisValue(...)`로 계산하는데 **넘기려야 넘길 수 없는** 구조 |
| BE-02 | `collector/src/**`에서 `rollback`·`autocommit` 전수 Grep | ✅ 일치 — 둘 다 **0건**(`_ROLLBACK_THRESHOLD`는 무관한 시각 보정 상수) |
| BE-03 | `AliasDictionary.java:14` · `CatalogProjection.java:66` | ✅ 일치 — `aliases = Map.copyOf(aliases)` ← `new HashMap<>()`. 순회 순서 비결정성 경로가 실재 |
| FE-01 | `DecisionPage.tsx:112-129` | ✅ 일치 — 121행 조기 `return`이 124행 `setLoaded(null)`보다 앞. 게다가 115-117행 주석은 **축 값의 잔존은 의식해서 막았는데**(`setDemandAxisValue(null)`) 조회 결과의 잔존은 못 막았음을 보여준다 |
| FE-02 | `PurchasePanel.tsx:74-78` | ✅ 일치 — `useEffect`에 cleanup 없음 |
| FE-03 | `UsedComparisonPage.tsx:130-142` | ✅ 일치 — `reload()`·`useEffect` 모두 가드 없음. **141행에 `eslint-disable-next-line react-hooks/exhaustive-deps`가 붙어 있는데 이 저장소엔 eslint가 없다** — 억제 주석이 "검토했다"는 거짓 표식으로 남아 있다 |
| X-01 | `check-network-optin.sh:41-44, 64` | ✅ 일치 — 주석 제거(`grep -vE '^[[:space:]]*#'`)를 **URL 탐지에만** 적용하고, opt-in 변수 검사 `grep -qE 'ALLOW_REAL_ROBOTS\|COLLECTOR_ALLOW_NETWORK' "$target"`(64행)은 파일 전체를 본다 |

## 이 리뷰가 스스로 정정한 것
리뷰어들이 **계획 단계의 전제 두 개를 실측으로 뒤집었다** — 다음 리뷰가 같은 전제로 출발하지 않도록 기록한다.
1. `GET /api/v1/coupang/variants/{id}/latest-price`의 인증 비대칭은 결함이 아니다 — **이 프로젝트는 GET에 앱 레벨 인증을 두지 않는 것이 일관된 설계**다(접근 통제는 nginx Basic Auth 계층).
2. `GlobalSettingsController` "테스트 0건"은 부정확했다 — web에는 테스트가 있고, **core 쪽 HTTP 레벨 테스트 부재**가 진짜 갭이다.

## 방법의 한계 (다음 리뷰가 덮어야 할 것)
- **정적 검증만 했다.** 실행·부하·동시성·실데이터 검증은 하지 않았다. 레이스(FE-02·FE-03)와 커넥션 오염(BE-02)은 정적으로 *가능성*까지만 확인했다.
- **"기계가 잡았을 항목" 집계가 부분적이다.** `30-cross-cutting.md` X-06은 C3·D1·D2의 13건만 대상으로 세어 2건(≈15%)을 얻었다 — **`10-backend.md`의 30건은 이 집계에 안 들어갔다.** 2차에서 도구 도입을 결정하려면 백엔드 항목도 같은 기준으로 세야 한다.
- **커버리지를 재지 않았다.** 테스트 부재는 파일 단위로만 확인했고 분기 커버리지는 보지 않았다.
- 각 리뷰어의 `## 시간·범위 한계로 못 본 것` 절이 다음 리뷰의 입력이다 — `raw/`에서 그 절만 모아 읽으면 된다.

## 다음(2차)을 위한 메모
1. **High 7건부터.** 전부 TDD(Red→Green)로 — 특히 FE-01·FE-02·FE-03은 같은 계열(상태 초기화·취소 가드)이라 **한 패턴으로 묶어 고치고 그 패턴을 테스트로 못박는 것**이 낫다. 형제 5개 파일이 이미 쓰는 `let live` 가드가 정본이다.
2. **X-01은 게이트 자신의 결함**이라 우선순위가 높다 — 게이트가 거짓 초록을 내면 그 아래 모든 검사가 무의미해진다.
3. **게이트화 가능성을 함께 판정하라**(CLAUDE.md 프로토콜). FE-02·FE-03 계열은 `eslint-plugin-react-hooks`가 잡는 유형이고, X-01·X-02는 각 게이트의 `.test.sh`에 **양방향 시험**을 추가하면 재발을 막는다.
4. **정적분석 도구 도입 순서**(X-06 권고): web eslint+react-hooks → collector ruff+mypy → core checkstyle/spotbugs. 이번 리뷰에서 실제로 적중한 것은 web뿐이므로 그 순서다.
