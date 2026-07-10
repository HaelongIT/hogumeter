# docs — 기획·설계 정본 인덱스

이 폴더가 hogumeter의 **정본**이다. 코드·git과 충돌하면 최신 코드가, 기획 정책이 충돌하면 [`90-planning-final.md`](90-planning-final.md)(확정본)가 최종 권위.

## 처음 보는 사람 읽기 순서
1. [`00-overview.md`](00-overview.md) — 용어집·핵심 수치 (개념 잡기)
2. [`01-architecture.md`](01-architecture.md) — 모듈 경계 (코드 구조 이해)
3. [`02-domain-model.md`](02-domain-model.md) — 개체·DealEvent 상태기계
4. [`90-planning-final.md`](90-planning-final.md) — 기획 확정본 v1.3 (전체 결정의 근거·최종 권위)
5. 관심 기능 문서(아래) → 기준가 상세는 [`benchmark/`](benchmark/)

## 개념·구조
| 문서 | 내용 |
|---|---|
| [`00-overview.md`](00-overview.md) | 용어집(코드 네이밍)·핵심 수치·표본 3단 규칙 |
| [`01-architecture.md`](01-architecture.md) | 컴포넌트 토폴로지·헥사고날 경계·DB 계약 |
| [`02-domain-model.md`](02-domain-model.md) | 개체·스키마 방향·DealEvent 상태기계 |
| [`03-deal-sets-and-time.md`](03-deal-sets-and-time.md) | **(2차 기반)** 딜 집합 3분·시간 좌표계·as-of 규약 |

## 기능 요구사항
| 문서 | 기능 | 단계 |
|---|---|---|
| [`10-feature-registration.md`](10-feature-registration.md) | 제품 등록 (REG) | 신품 코어 |
| [`11-feature-benchmark-engine.md`](11-feature-benchmark-engine.md) | 수집·매칭·기준가 엔진 (BM) | 신품 코어 |
| [`12-feature-monitoring-alerts.md`](12-feature-monitoring-alerts.md) | 감시·알림 (AL) | 신품 코어 |
| [`13-feature-price-comparison.md`](13-feature-price-comparison.md) | 구매 시점 판매처 비교 (CMP) | 기능4 |
| [`14-feature-used-market.md`](14-feature-used-market.md) | 중고 (USED) | 기능5 |
| [`15-feature-purchase.md`](15-feature-purchase.md) | **(2차)** 구매 기록·관찰·성적표 (PUR) | M5 |
| [`16-feature-signal-cadence.md`](16-feature-signal-cadence.md) | **(2차)** 신호등·딜 주기 (SIG·CAD) | M5 |
| [`17-feature-watchlist.md`](17-feature-watchlist.md) | **(2차)** 딜 보관함 (WATCH) — 조건부 스텁 | M6 |
| [`18-feature-digest.md`](18-feature-digest.md) | **(2차)** 주간 다이제스트 (DIGEST) | M5 |
| [`19-feature-priority.md`](19-feature-priority.md) | **(2차)** 우선순위 (PRI) — ②축소 | M6 |

## 기준가 엔진 모듈 상세 (benchmark/)
[`benchmark/`](benchmark/) — 개발 중인 핵심 모듈의 상세. `04-functional-requirements.md`가 **M1 TDD의 인수조건 정본**.
`00` 개요 · `01` 아키텍처·컨벤션 · `02` 데이터모델 · `03` API · **`04` 인수조건(=TDD 기준)** · `05` 비기능 · `06` TDD · `07` 에러코드.

## 비기능·전략
| 문서 | 내용 |
|---|---|
| [`20-non-functional.md`](20-non-functional.md) | 보안·성능·신뢰성·관측성 |
| [`21-tdd-guidelines.md`](21-tdd-guidelines.md) | TDD 규율·테스트 전략·2차 테스트 매트릭스 |
| [`30-roadmap.md`](30-roadmap.md) | 마일스톤 M0~M6·스파이크 |
| [`31-detailed-params.md`](31-detailed-params.md) | 확정 수치 파라미터(승인)·위임 수치 |

## 살아있는 보드 (매 세션 갱신)
| 문서 | 성격 |
|---|---|
| [`91-open-questions.md`](91-open-questions.md) | 기술 보류(잠정값 + 재개 트리거) |
| [`98-field-notes.md`](98-field-notes.md) | 사이트별 실측(셀렉터·응답 특성·차단 징후) |
| [`99-lessons.md`](99-lessons.md) | TDD·디버깅 교훈 누적 |
| [`../working-area/`](../working-area/) | **`progress-log`(무중단 중 알릴 것, 최신 위)** · 결정 보드(정할 것/정한 것) · 배포 체크리스트 |

> 문서 지도의 요약 버전은 [`../CLAUDE.md`](../CLAUDE.md) `## 문서 지도`에도 있다(작업용).
