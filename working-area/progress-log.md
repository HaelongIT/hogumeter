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

## 2026-07-10 — OBS-01 core JSON 로그 (Q-57 ① 해소) + 문서 드리프트 정정

- **한 일**: ① Spring Boot 4.1 내장 구조화 로그를 **환경변수로만** 켰다 — `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(compose `CORE_LOG_FORMAT`). `application.yml`도 `logback-spring.xml`도 만들지 않았다(**core 파일 무수정**). 이제 core도 collector처럼 JSON을 낸다. 스모크 5-1b가 매번 확인한다(형식은 조용히 되돌아간다). ② `docs/01`·`README`·`core/README`가 **"누가 언제 `raw_deal_post`를 소비하는가"를 말하지 않았다** — 바로 그 침묵이 시스템을 죽여뒀다. 파이프라인 트리거 절을 셋 다 신설.
- **자율로 정한 것**: 로그 형식 기본값 `ecs`(가장 널리 읽히는 스키마). `CORE_LOG_FORMAT=`(빈 값)으로 텍스트 로그로 되돌릴 수 있다 — seam 1곳.
- **발견**: 첫 스모크가 실패했는데 코드가 아니라 **내 단언이 틀렸다**. ECS는 필드를 중첩한다(`{"ecs":{"version":…}}`, `{"log":{"level":…}}`). 대상보다 하네스를 먼저 의심하라는 규칙을 또 확인했다.
- **⚠️ 당신이 볼 것**: 없음.
- **다음**: 남은 미추적 요구 계속 훑기.

## 2026-07-10 — 무중단이 끊기던 진짜 이유 (지침 개정)

- **한 일**: 정지 원인을 트랜스크립트로 실측(`ExitPlanMode` 11 + `AskUserQuestion` 12 = 23회 강제 정지)하고 CLAUDE.md를 개정 — 턴 규율 / 질문 규율(`AskUserQuestion`은 정지조건에만) / 정지조건 정밀화(보안 게이트를 **넓히는** 변경만 정지) / **모듈 소유권 절 신설**(core=상대, additive 신규 파일은 자율) / Git 규칙 현실화.
- **핵심 발견**: 지침의 "보고는 멈춤이 아니다"가 **이 도구에선 거짓**이었다. 도구 호출을 멈추고 채팅 글을 내면 그 턴이 끝나고, 사용자가 말하기 전까지 아무것도 못 한다. 지침이 "비차단"이라 부른 행위가 정확히 차단이었다.
- **⚠️ 당신이 볼 것**: **플랜 모드는 사용자만 끌 수 있다.** 켜져 있으면 `"This supercedes any other instructions"`라 CLAUDE.md가 무엇을 적든 매 턴 `ExitPlanMode` 승인을 받아야 한다. `Shift+Tab`으로 모드를 순환해 plan이 아닌 상태로 둘 것. (선택) `.claude/settings.local.json`에 `{"permissions":{"defaultMode":"acceptEdits"}}` — `git push` deny는 그대로 유지된다.
- **하지 않은 것**: 정지조건 완화 없음. `permissions.deny`의 `git push`·`guard.sh` 무수정(오늘 jar를 깎은 사고가 그 게이트의 값어치를 증명했다).
- **다음**: 개정된 지침대로 무중단 재개.

## 2026-07-10 — Q-27 ① 가격 변경 재처리 (BM-01 AC-2 나머지 절반)

- **한 일**: 수집기가 업서트한 새 가격이 `deal_event`까지 가지 못하던 것을 뚫었다. 순수 `PriceRefresh`(산술) + 신규 `ReprocessDealPricesUseCase`(IO). 스케줄러가 **ingest → 가격 → 종료** 순으로 돈다. **기존 core 파일 무수정**(`DealEventMapper.toDomain`으로 crossVerified를 복원해 `applyMerge`를 그대로 씀). 스모크 5-1c가 `999000/899000/899000`(first 불변 / min 갱신 / last 갱신)을 증명한다.
- **자율로 정한 것**(되돌리기 쉬움, decision-log 참조): ① `priceLast`의 후보는 **활성 원문뿐** — 방금 품절된 800,000원을 "지금 가격"이라 말하지 않는다. ② 그래도 그 800,000원은 `priceMin`("지나간 기회")에 남는다. ③ 동시각 관측이 여럿이면 더 싼 쪽. ④ 종료 단계를 가격 뒤에 둬서 닫히기 직전의 마지막 가격까지 반영. ⑤ 바뀐 게 없으면 쓰지 않는다(빈 갱신이 lastSeen만 흔들면 "언제 실제로 변했나"를 잃는다).
- **⚠️ 당신이 볼 것**: 없음. Q-27 잔여는 이제 ②변경 감지기(효율) ③최초부터 품절인 원문 ④애매/스킵 글 재스캔 — 전부 core 기존 파일 수정이 필요해 상대와 조율.
- **다음**: PERF-01~04·OPS-01이 여전히 어느 보드에도 없다. 추적부터 시킨다.

## 2026-07-10 — OBS-02 파이프라인 카운터 (Q-57 신설)

- **한 일**: `PipelineScheduler`가 매 틱 `PipelineTickReport`를 남긴다 — `postsLinked · dealsCreated · merged · queued · ended · pending · rawTotal`. 병합률은 "링크는 늘었는데 딜은 안 늘었다"로 **유도**한다(직접 셀 수 없다). 0을 생략하지 않는다. 스모크가 실 로그에서 `dealsCreated=1 merged=0 pending=0`을 확인한다. 리포지토리·유스케이스 **무수정**(JpaRepository의 `count()`만 씀).
- **자율로 정한 것**: `pending`을 `rawPosts − sources` 뺄셈으로 근사하지 않고 `findUnprocessed().size()`로 정확히 잰다 — `unique(deal_event_id, raw_deal_post_id)`는 한 원문이 두 딜에 붙는 걸 막지 않아 그 뺄셈은 가정이다. 1인용 규모라 스캔 비용은 무의미.
- **⚠️ 당신이 볼 것**: `docs/91` **Q-57 신설** — OBS-01/02가 **어느 보드에도 없던 요구**였다. core는 여전히 **JSON 로그가 아니고**(logback 기본 텍스트), 매칭 tier 비율·알림 발송 수는 use case가 void라 밖에서 셀 수 없다(core 기존 파일 수정 필요 → 상대와 조율).
- **다음**: 요구 문서(docs/20) 전수 대조를 계속한다.

## 2026-07-10 — SEC-05 크기 상한 (Q-55 해소)

- **한 일**: 크롤링 텍스트가 상한 없이 `text`·`jsonb`로 들어가던 것을 막았다. title 300자 · url 2000자 · post_id 64자 · raw 256KiB(**바이트**로 잼 — 한글은 UTF-8에서 3바이트라 글자 수로 재면 3배 뚫린다). 넘으면 **자르지 않고 거절**하고 `oversized` 이벤트로 남긴다. `cycle.skipped` 카운터 신설(0도 센다).
- **자율로 정한 것**: 상한값 4개 — golden 89건 실측 최대(title 62 · url 75 · post_id 11 · raw 57B)의 수 배. **전수 대조로 오차단 0건 확인**. 잠정값이며 seam은 그 상수 4개.
- **⚠️ 당신이 볼 것**: 없음.
- **왜 자르지 않았나**: 잘린 제목은 여전히 제목처럼 생겨서 매칭이 별칭을 놓치고 가격 파싱이 뒤쪽 `(999,000원/무료)`를 잃는다 — 결과는 "가격 없음 → 스킵"이라 **조용하다**. 거절하면 이벤트가 남아 사람이 원문을 본다.
- **다음**: 남은 코드 가능 항목을 다시 훑는다(요구 문서 `docs/20` 대조 포함 — 보드에 없는 요구는 없는 일이 된다).

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
