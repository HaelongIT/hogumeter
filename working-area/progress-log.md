# 진행 로그 (Progress Log) — 무중단 개발 중 사용자에게 알리는 것

> 무중단(Autonomous)으로 개발하는 동안 **사용자가 알아야 할 것**을 여기 차곡차곡 쌓는다.
> 채팅이 흘러가거나 자리를 비워도 여기만 보면 "그동안 뭐가 있었나"를 따라잡을 수 있다.
> 규칙: 매 배치 매듭(여러 커밋 후·마일스톤·블로커)마다 **최신을 맨 위에** append. 지우지 않는다.
> ⚠️ = 당신의 확인/결정이 필요한 것(해당 보드 링크 병기). 나머지는 FYI.
>
> 항목 형식:
> ```
> ## <날짜> — <한 줄 제목>
> - 한 일: … (커밋 해시)
> - 자율로 정한 것: … (되돌리기 쉬움 · decision-log 참조)
> - ⚠️ 당신이 볼 것: … (decisions-needed D-x / docs/91 Q-y) — 없으면 "없음"
> - 다음: …
> ```

---

## 2026-07-10 — M1 종단 루프를 이었다 (파이프라인 트리거 신설)

- **한 일**: `ingestPending()`·`reprocessEndedDeals()`를 **프로덕션에서 부르는 사람이 없다는 것**을 발견하고(`@Scheduled` 0건) `adapter/scheduler/PipelineScheduler` + `SchedulingConfig` 신설. ingest→reprocess 순서, 단계별 예외 격리, `initialDelay=interval`. **기존 core 파일 수정 0**(신규 4파일). 스모크 5-1b 신설 — `raw_deal_post` 한 행을 넣으면 `deal_event`가 생기고 기준가 REST가 `NONE → SPARSE(n=1)`로 바뀌는 것을 매번 증명한다.
- **자율로 정한 것**: 주기 기본 60s(`core.pipeline.interval-ms`, 게시판 폴링 하한과 정합) / 실패 시 `log.error` 후 다음 단계·다음 주기 계속(뭉개지 않되 죽지도 않는다) / 테스트에선 스케줄러 전역 off. 전부 되돌리기 쉬움 — decision-log 2026-07-10.
- **⚠️ 당신이 볼 것**: `docs/91` **Q-56 신설** — 파이프라인 단계 실패가 **로그에만** 남는다. 관리 알림은 텔레그램 토큰(Q-20) 대기. 그때까지 `docker logs`가 유일한 창구다.
- **발견**: 스모크가 처음엔 실패했는데 원인은 스케줄러가 아니라 **내 스모크의 greedy `sed`**였다(마지막 variantId=512GB를 집어 딜이 붙은 256GB 대신 조회). 라벨로 고르도록 고쳤다.
- **다음**: SEC-05 크기 상한(Q-55) — collector가 크롤링 텍스트를 상한 없이 적재한다. 자르지 않고 거절+이벤트.

## 2026-07-09 — 무중단 지침 강화 + 진행 로그 신설

- **한 일**: CLAUDE.md `## Autonomous(무중단) 모드`를 "스스로 계획하며 쭉 개발"로 재작성(증분 사이 무정지·self-plan·되돌리기 가능=자율·배치 비차단 보고). 이 진행 로그 파일 신설 + 루프엔드 라우팅 표에 편입.
- **자율로 정한 것**: 진행 로그 파일명·위치(`working-area/progress-log.md`)·형식(최신 위 append) — 되돌리기 쉬움. (decision-log 참조)
- **⚠️ 당신이 볼 것**: 없음(당신이 승인한 지침 변경).
- **다음**: 새 지침대로 무중단 개발 재개 — 남은 기획 읽어 다음 막히지 않은 증분 자율 선택.

## 2026-07-09 — CI secrets(gitleaks) 빨간불 수정

- **한 일**: CI `secrets` 잡이 `.githooks/pre-commit.test.sh`의 **가짜 fixture 자격증명 3건**을 오탐하던 것 해결. `.gitleaks.toml`에 좁은 예외(그 파일·`curl-auth-user` 규칙 한정) 추가. 로컬 gitleaks 전체 히스토리 스캔 GREEN 확인(커밋 `9a9045e`).
- **자율로 정한 것**: 예외를 기존 `smoke.sh` 스타일대로 최대한 좁게(condition=AND) — 되돌리기 쉬움.
- **⚠️ 당신이 볼 것**: 진짜 유출 아니었음(테스트용 가짜값). **이 커밋을 푸시하면 CI secrets 통과**.
- **다음**: (지침 작업으로 전환됨)

## 2026-07-09 — Q-27 상태변화 재처리 (SOLD_OUT → deal_event ENDED)

- **한 일**: 수집기 업서트(Q-36)로 `raw_deal_post`엔 반영되나 core가 못 읽던 상태변화를 `deal_event.ENDED`로 전파. 신규 `ReprocessDealStatusUseCase` — 링크된 **모든** 원문이 종료됐을 때만 ENDED(한 소스라도 ACTIVE면 유지). `findUnprocessed`·`IngestDealsUseCase` **무수정**, additive 메서드 2개만. core 264 tests GREEN(커밋 `11368c0`).
- **자율로 정한 것**: 다중소스 종료 규칙(전부 종료 시만)·last_seen 단조·additive 격리 — decision-log 2026-07-09 행.
- **⚠️ 당신이 볼 것**: 없음. 잔여(가격변화 재처리·배치 오케스트레이션)는 docs/91 Q-27에 열림.
- **다음**: (지침 작업 요청으로 전환)

## 2026-07-08~09 — M5 배선 + 수집기 적재 준비 (여러 증분)

- **한 일**: SIG·CAD 조회 REST(`ab64eee`), PUR-02 스냅샷(`6d58ad7`)·PUR-01 구매기록 영속(`041c370`)·PUR-05 관찰문맥(`a874b94`)·PUR-03 "산 뒤 알림" 트리거(`ffb8456`), collector 적재 준비(`073f96c`). 각 GREEN 후 커밋.
- **자율로 정한 것**: PUR 스냅샷 인라인 컬럼·as-of 방식, PUR-03 additive 트리거 등 — decision-log·docs/91(Q-34·35) 참조.
- **⚠️ 당신이 볼 것**: **미푸시 커밋들 — 푸시는 당신이** (`11368c0`·`9a9045e`·이번 지침 커밋이 로컬에만 있음). / 열린 결정 **D-3**(차단 사이트 재개 경로)·**D-4**(HTTPS 종단)·**D-5**(0원 딜 표본 포함) — `working-area/decisions-needed.md`. / 1차 검증은 여전히 키(네이버 Q-3·텔레그램 Q-20)·실폴링 승인 대기.
- **다음**: 무중단으로 코드-가능 최고가치 증분 계속(예: Q-27 잔여, roadmap 다음 항목).
