# 결정 로그 (Decision Log)

> **확정된 결정 기록** — "무엇을 왜 그렇게 정했나". 미래의 나/리뷰어가 "왜 이렇게 했지?"의 답을 찾는 곳.
> `decisions-needed`에서 정해진 것, `docs/91`에서 해소된 것을 여기로 옮긴다. 1줄~1문단.

| 날짜 | 결정 | 근거/주체 | 영향 문서·커밋 |
|---|---|---|---|
| 2026-07-04 | 스캐폴드 병합 방식 = 기존 CLAUDE.md·docs 체계 유지 + 템플릿 운영 장치(working-area·기록강제·autonomous)만 이식 | 사용자 결정(셋업 인터뷰) | CLAUDE.md, working-area/* |
| 2026-07-04 | 빌드 도구 확정 — core: Gradle(Kotlin DSL)+JUnit 5+Testcontainers / collector: uv+pytest / web: Vite+React+TS / CI: GitHub Actions | 사용자 결정(셋업 인터뷰) | CLAUDE.md §빌드·테스트 명령 |
| 2026-07-04 | 첫 개발 모듈 = benchmark(기준가 엔진, BM) — "시스템의 심장", 순수 도메인이라 TDD 출발점 최적 | 사용자 결정(셋업 인터뷰) | docs/benchmark/ |
| 2026-07-04 | Autonomous(무중단) 범위 = 테스트로 검증 가능한 전 구현. 정지 조건: 데이터 파괴·외부 실발송(텔레그램 실전송·실사이트 크롤링)·비용·보안 정책·기획 확정본 충돌 | 사용자 결정(셋업 인터뷰) | CLAUDE.md §Autonomous |
| 2026-07-04 | 운영 DB는 자체 호스팅 Postgres 직접 관리 → 템플릿 §8(관리형 DB 규칙)·schema-change-queue.md 미이식 | docs/01-architecture(RDS 금지·이식성 원칙) | working-area/, CLAUDE.md |
| 2026-07-04 | loose-end 라우팅 번호 매핑 — 기술 보류 보드=docs/91-open-questions(90번은 기획 확정본 선점), 교훈=docs/99-lessons, 실측=docs/98-field-notes | 기존 문서 체계 우선 원칙 | CLAUDE.md §작업 방식 |
| 2026-07-04 | (D-1) 디렉토리명 끝 공백 리네임 = **세션 종료 후 사용자가 직접**. 세션 중엔 절대경로 따옴표 처리, 스캐폴딩은 전부 상대경로라 리네임 비용 0 | 사용자 결정(인터뷰) | docker-compose·CI 상대경로 |
| 2026-07-04 | M0 빌드 도구 실체화 — Boot 3.5.16/Gradle 8.14.5(KTS)/Java 21, collector uv+pytest, compose postgres16, CI GitHub Actions | M0-1 스캐폴딩 | core/, collector/, docker-compose.yml, .github/ |
| 2026-07-04 | Flyway V1 = 신품 코어 루프 11테이블만. used(중고)는 M2 V2 이월 | M0-3 + docs/91 Q-4 | core V1__init.sql |
| 2026-07-04 | Claude Code 권한 = acceptEdits + 프로젝트 배시 허용목록(.claude/settings.local.json, gitignore). 파괴적 명령(rm 등)은 계속 승인 요구 — autonomous 정지조건과 정렬 | 사용자 요청(승인 팝업 과다) | ~~.claude/settings.local.json~~ — **⚠️ 2026-07-09 확인: 그 파일은 존재하지 않는다**(gitignore 대상이라 D-1 디렉토리 리네임 때 유실 추정). 아래 2026-07-09 행으로 대체 |
| 2026-07-04 | **(D-2) Spring Boot 3.5.16 → 4.1.0 이관.** 스택 확정본의 "Boot 3.x 변경 금지"를 개정. **4.0.x가 아니라 4.1.0 채택** — 4.0.x는 2026-12-31 OSS EOL이라 D-2의 목적(임박 EOL 회피)과 모순, 4.1.0이 최신 안정판·최장 지원. M0 스캐폴드 저표면적 시점에 이관 | 사용자 결정(D-2, "최신 지원 확보 + 문서 반영") | core/build.gradle.kts, docs/90 §7, CLAUDE.md 스택, docs/99 |
| 2026-07-04 | Boot 4.1 이관 fallout 확정: web→webmvc 스타터 리네임, flyway는 spring-boot-starter-flyway(+flyway-database-postgresql 별도 유지), Testcontainers 2.0(아티팩트 testcontainers-* 접두사·패키지 org.testcontainers.postgresql·self-type 제네릭 제거) | Boot 4.1 BOM 실측(./gradlew test GREEN) | core/build.gradle.kts, TestcontainersConfiguration.java |
| 2026-07-04 | **docs/31 수치 파라미터 6개 승인**(운영자): MERGE_PRICE_TOLERANCE ±2%(min ±5,000), MERGE_WINDOW_HOURS 48, OUTLIER_IQR_MULTIPLIER 1.5, COLDSTART_JACKPOT_RATIO 0.30, K_DISPLAY 5(3~10), EXPAND_LIMIT_MONTHS 12. `BenchmarkParams.defaults()`로 상수화, 도메인은 주입 params만 참조. docs/91 Q-1 해소 | 사용자 승인(제안값 그대로) | docs/31, BenchmarkParams |
| 2026-07-04 | `BenchmarkParams`(수치 seam)를 `domain.benchmark` → **`domain` 루트로 이동**. benchmark·deal·matching 공통 참조 공유 커널이라, `benchmark→deal`(BenchmarkCalculator)와 `deal→benchmark`(DealMergePolicy) **패키지 순환**을 끊음. 같은 이유로 `Quantiles`도 domain 루트로 이동(+P75) | BM-04·05 착수 시 순환 발견 | core domain/BenchmarkParams.java·Quantiles.java |
| 2026-07-04 | collector 런타임 의존 **beautifulsoup4** 추가 — 루리웹·펨코 HTML 파서(번개는 JSON, stdlib). krepe90 골격도 bs4 사용. `uv add` → pyproject·uv.lock 갱신 | BM-01 파서 착수 | collector/pyproject.toml, uv.lock |
| 2026-07-08 | **2차 기획 통합** — `working-area/2nd-plan-intake.md`를 1차 문서 체계에 반영. 신규 기능 6(PUR/SIG/CAD/WATCH/DIGEST/PRI) + 공통 기반(집합 명명·시간 좌표계·가격 3분법·as-of)·소급 정정. 새 문서 docs/03·15·16·18(+17·19 조건부 스텁), 편집 00·01·02·10·11·12·21·30·31·CLAUDE.md | 사용자 승인(통합 방침·밀도·유보 처리) | docs/03·15·16·17·18·19 신규, docs/00·01·02·10·11·12·21·30·31·90·91·CLAUDE.md |
| 2026-07-08 | **확정본 v1.2→v1.3 개정** (2차 통합 감사 6건 확정) — C-1 DealEvent `ENDED→ACTIVE` 재개 전이 채택 / C-2 firstSeen=발생시각(postedAt 우선, **라이브는 M0 실측 후 확정**=Q-23) / C-3 K_fill 상한 `min(12개월, observedFrom)` / C-4 이상치 유입 1회·영속(드리프트 재평가 없음, 변경 서열 사람>배치>잠정) / C-5 ⚠️라벨=전 통계 제외(가시성만 차등) / C-6 미선택 수요축값=전 집합 제외("범위 외", 비용 고지) | 사용자 일괄 승인(AI 권고 채택, decisions-needed DN-C1~6) | docs/90 §13 v1.3 델타 + [v1.3] 주석, docs/02·10·11·12 |
| 2026-07-08 | **알림 발송 단위 원칙**(B-5) — 1차 "최고 강도 1발"을 "관측 대상 상태 사건당 1통(최고 우선 프레이밍+나머지 병기)"의 **트리거 부분집합**으로 재배치. 3계열 표(트리거/후속/정기)·라이브 재평가·비대칭(알림=재현율/상시표시=단정) | 사용자 승인(B-5) | docs/12, docs/90 §3 [v1.3] |
| 2026-07-08 | M1 핵심 배선 슬라이스 1(등록) — API 컨벤션 **봉투 없는 리소스 직접 반환** 착수 확정(Q-2, 에러 형식 `{code,message}`는 슬라이스 2에서 @ControllerAdvice로). Boot 4 슬라이스 테스트용 `spring-boot-starter-webmvc-test` 추가(docs/99) | M1 web 슬라이스 착수 | core build.gradle.kts, adapter/web·persistence, application |
| 2026-07-08 | **PUR 기록 지속 배선**(V2 마이그레이션) — `purchase` 테이블 단일. PUR-02 스냅샷은 **별도 테이블 아닌 동일 행 인라인 컬럼**(snap_*, 1:1 동결·가역적). Purchase는 variant 비점유(복수 공존). as-of 스냅샷 = `Clock.fixed(purchasedAt)`로 기준가 재산출(firstSeen>purchasedAt 딜은 창 밖 제외), observedFrom=최초 firstSeen 이전 구매는 UNOBSERVED. capturedAt 미보유 한계는 Q-32, observedFrom 잠정은 Q-34 | M5 PUR 배선(승인된 docs/15 설계 구현) | core V2__purchase.sql, PurchaseEntity·Repository, RecordPurchaseUseCase·Command, PurchaseController |
| 2026-07-08 | **WATCH 개념 채택·배치 유보** — 딜 보관함은 루프 유효 빈칸이나 PUR/SIG 안착 후 검증. docs/17 조건부 스텁 유지, 배치 M6. **PRI ② 축소 채택** — 목록 정렬 옵션만 저비용 반영, 알림 병기·participation은 드랍(실사용 필요 확인 시 재론). **마일스톤**: M5=SIG·CAD→PUR→DIGEST / M6=WATCH·PRI | 사용자 승인(DN-W·DN-P·DN-M) | docs/17·19·03·12·30 |
| 2026-07-09 | **기록 하네스를 훅으로 강제.** CLAUDE.md는 공식 문서상 "context, not enforced configuration"이고, 실측으로도 `## 축적된 규칙` 승격이 **교훈 6건 동안 0건**이었다(자동 로드되는 파일의 규칙이 6/6 무시됨). ① `SessionStart` 훅이 열린 보드 + **기록 드리프트 지표**를 매 세션·매 압축 후 주입 ② `PreToolUse` 훅이 결정론적 정지조건(`git push`·실사이트/외부 API 호출)을 물리적 차단. **판단이 필요한 규칙(임의 브랜치 전환 등)은 훅으로 옮기지 않는다** — 훅은 결정론용 | 사용자 요청(하네스 충분성 감사) | .claude/settings.json·hooks/, CLAUDE.md |
| 2026-07-09 | **교훈 승격 2계층화.** 보편 규칙은 CLAUDE.md `## 축적된 규칙`, **언어·디렉토리 한정 규칙은 `.claude/rules/<scope>.md`**(`paths:` frontmatter → 매칭 파일 열 때만 로드). 근거: 교훈 8건 중 4건이 Boot 4 함정(core 전용)이라 전역에 두면 collector 작업 때 컨텍스트를 낭비하고, 안 두면 상기되지 않는다 | 사용자 승인(2계층 개정) | CLAUDE.md, .claude/rules/core-java.md·collector-python.md |
| 2026-07-09 | **permissions 배치 분리** — 프로젝트 사실(테스트 명령 allow, `git push` deny)은 커밋되는 `.claude/settings.json`, 개인 취향(`defaultMode`)은 gitignore된 `settings.local.json`. 2026-07-04 결정(전부 local)의 개정 — 하네스가 저장소와 함께 이동해야 상대에게도 적용되기 때문. ⚠️ 최초 기재의 "`Bash(git add:*)` 콜론+별표가 정확한 문법"은 **오류** — 공식 문서상 `:*`와 ` *`는 **동등**하고(`Bash(ls:*)` ≡ `Bash(ls *)`), 컴파운드 명령은 하위명령별로 독립 매칭된다 | 하네스 감사 | .claude/settings.json |
| 2026-07-25 | **DIGEST 설계 결정 3건(docs/18 Q-81)** — ⑥ 관측 공백 = 지금은 "공백 없음" 고정(관측시계 연결은 후속, 되돌리기 쉬움) / 스케줄 = 고정 상수 일요일 20시 KST 먼저(사용자 설정은 refactor seam) / ⑤ "N회째 미확인" = `review_queue_item`에 카운터 컬럼 추가(다이제스트에 담길 때마다 +1) | 사용자 결정(AI 권고 전부 채택) | `docs/91` Q-81, DIGEST 후속 유스케이스 |
| 2026-07-24 | **D-6 구현 상태(부분)**: 후보 선정(alias_dictionary 읽기 + 순수 결정 함수)까지 배선, 실 fetch·파서는 루리웙 상세 페이지 fixture 대기(`docs/91` Q-80) | 구현 진행 기록 | `collector/db/alias_source.py`·`pipeline/detail_fetch.py`·`__main__.py` |
| 2026-07-24 | **D-6: 루리웹 잘린 제목 가격 복구 = 등록 제품 별칭 걸리는 잘린 제목만 상세 fetch(②안).** 전부 상세 fetch(①, 사이클당 최대 부담)·안 함(③, 놓침 감수) 대신 채택 — 사이클당 대개 0~1건만 추가 요청, 우리가 알림 낼 딜의 가격만 복구해 목적에 정확히 부합. `raw_deal_post.body_text`(Q-18 죽은 컬럼)가 저장 자리 | 사용자 결정(AI 권고 ②안 채택) | `collector/src/collector/parsers/ruliweb.py` + `raw_deal_post.body_text` |
| 2026-07-24 | **D-5: 0원(무료) 딜 = 표본 제외 + 알림·표시엔 태운다(B안).** 스킵 유지(A)·표본 포함(C) 대신 채택 — pricingSet/signalSet 분리(docs/03)에 정합, median·P25 산식 오염 없이 "무료 배포"도 놓치지 않는다 | 사용자 결정(AI 권고 B안 채택) | `collector/src/collector/pipeline/price.py` 반환값 + core pricingSet 필터 |
| 2026-07-24 | **D-4: HTTPS 종단 = Caddy를 앞단에(A안), 구현 완료(스캐폴딩).** nginx+certbot(B)·Cloudflare Tunnel(C)·VPN만(D) 대신 채택. `Caddyfile`(`{$DOMAIN:localhost}` → 실 도메인이면 Let's Encrypt 자동, 미설정이면 내부 CA 자체서명) + `docker-compose.yml`의 `caddy` 서비스(`public` 프로파일로 격리 — 기본 `up -d`로는 안 뜸, `--profile public` 명시 필요). **여전히 사람이 할 것**: `.env`의 `DOMAIN` + 서버 80/443 방화벽 + `--profile public up -d`(`pre-deploy-checklist.md` §C) | 사용자 결정(AI 권고 A안 채택) | `docker-compose.yml`·`Caddyfile`·`.env.example` |
| 2026-07-24 | **D-3: 차단된 사이트 재개 = 설정/DB 값 수동 수정(B안).** 텔레그램 봇 명령(A)·자동 재시도(C)·영구중지(D) 대신 최소 구현 채택 — 외부 의존 0, 되돌리기 쉬움, 나중에 A를 얹을 수 있다. Q-59(커서 영속화) 착수 전 선결 조건 해소 | 사용자 결정(AI 권고 B안 채택) | `collector/src/collector/scheduler/policy.py`의 `advance()` stopped 분기 |
| 2026-07-09 | **web 최소 슬라이스 착수 + core 읽기 전용 조회 API 추가.** 확정본 §7("등록 화면은 1차 검증 전 선개발")을 막던 유일한 구멍은 **등록 응답이 `productId`만 주는데 모든 조회가 `variantId`를 요구**한다는 것. `GET /api/v1/products`·`/{id}/variants` 신설(신규 파일 2개, 기존 파일 무수정 — 상대와 충돌면 0). web은 **CORS를 건드리지 않고 Vite 프록시로 우회**(core 설정 불변). REG-01 네이버 후보검색은 키 미발급(Q-3)이라 수동 폴백만, 그 이유를 화면에 명시. REG-03 설정 화면은 쓰기 REST 부재로 범위 밖(Q-48) | 사용자 승인("읽기 전용 조회 API 추가는 허용") | core/adapter/web/ProductQueryController, web/ |
| 2026-07-09 | **collector 신규 의존 승인** — 런타임 `psycopg[binary]`, 테스트 `testcontainers[postgres]`. 근거: DB 적재기 없이는 기준가 표본이 영원히 0. core가 이미 Testcontainers(Java)를 써 규율이 일관되고 Docker는 로컬·CI 모두 가용. 빠른 루프는 `pytest -m "not integration"`(1.3초)로 보전 | 사용자 승인 | collector/pyproject.toml |
| 2026-07-09 | **`raw_deal_post` 업서트 필드 정책 확정** — `(site, post_id)` 충돌 시 **변화 필드 전부 갱신**(url·title·captured_at·status·headline_price·reaction_score·raw). `posted_at`만 `COALESCE(기존, 신규)`로 **불변 + 후채움**(발생 시각, 확정본 C-2 / Q-23). 근거: BM-01 AC-2("상태 변화는 기존 행에 반영")와 core `RawDealPostUpsertTest`가 갱신을 단언한다. insert-only면 품절을 영원히 모르고, core 업서터와 동일하게 하면 가격·추천수 변화를 놓친다. **`docs/01`·README의 "collector insert-only" 서술을 정정**(문서가 실행 가능한 계약과 모순이었다). 발견: core의 `RawDealPostUpserter`는 프로덕션 미사용 — 쓰기 주체가 아니라 의미를 못박은 명세다 | 사용자 승인(실측 제시 후) | collector/db/raw_deal_sink.py, docs/01, collector/README |
| 2026-07-09 | **뽐뿌 fixture를 golden 정본으로 확정, 재채취 기각(Q-5 해소).** 기존 기록("baseList 셀렉터 전무 → 부적합")이 **오류**였다 — 실제로 없는 건 `#revolution_main_table` 요소 하나뿐이고 딜 행 21건은 정상 파싱된다. 결정 근거: **UA 위장 금지(원칙5)** 상 운영에서도 이 응답을 받으므로, 브라우저 UA로 재채취한 마크업을 golden으로 쓰면 프로덕션과 어긋난다. **받는 것을 파싱한다** | 사용자 승인(실측 제시 후) | parsers/ppomppu.py, fixtures/ppomppu/list_normal.html, docs/98·91·99 |
| 2026-07-09 | **`git push` 차단을 훅 → `permissions.deny`로 이관.** 문서의 관용구이고 프로세스 spawn이 0이며 하위명령별 매칭이라 `cd x && git push`도 잡는다. 훅은 **호스트 기반 네트워크 차단만** 담당 — 문서가 "Use PreToolUse hooks: implement a hook that validates URLs in Bash commands and blocks disallowed domains"라고 직접 권하는 영역. 훅 정의는 exec form + `${CLAUDE_PROJECT_DIR}`(문서: "Prefer exec form for any hook that references a path placeholder") + `timeout` 명시 | 하네스 검증(오차단 4건·규격 위반 2건 발견) | .claude/settings.json, .claude/hooks/guard.sh·guard.test.sh |
| 2026-07-09 | **읽기 전용 판단 화면(web `decision/`)을 M4에서 앞당김.** 확정본상 조회 화면은 M4(웹 마감)지만 M1의 잔여 블로커가 **전부 외부**(텔레그램 토큰 Q-20, 네이버 키 Q-3, 실폴링 승인)라 코드로 진행 가능한 최고가치 작업이었다. 범위는 **읽기 전용**(신호등·기준가·갭·주기) — core 변경 0, 새 의존 0(라우터 미도입), 승격 큐·비교는 여전히 후순위. 되돌리려면 `web/src/decision/` 삭제 + `main.tsx` 1줄. **부수 소득이 본체보다 크다**: 화면을 그리자마자 `currentPrice=0` 문제(Q-53)가 드러났다 — API는 갭을 `−100%`로 정상 응답한다 | 무중단 자율(되돌리기 쉬움, 테스트 GREEN) | web/src/decision/·App.tsx, scripts/smoke.sh 5-1단계 |
| 2026-07-09 | **REL-04 오프사이트 + REL-05 롤백을 "리허설로만 존재를 증명한다"로 확정.** 복구 경로(오프사이트 업로드·마이그레이션 롤백)는 사고가 나야 처음 실행되므로 **매 커밋 CI에서 실행**한다(`offsite`·`rollback` 잡). 오프사이트는 실 AWS 대신 **MinIO**(S3 호환)에 대고 운영과 같은 코드 경로를 돌린다 — 검증 대상은 AWS가 아니라 "S3 API에 대고 우리 스크립트가 옳게 도는가"이고, 실 호출은 비용·정지조건에 걸린다. 업로드 후 `head-object`로 크기 대조(“올렸다”≠“온전히 있다”). 롤백은 R2 신설 + **역순 강제**(purchase→variant 외래키; R1을 먼저 돌리면 8개 테이블이 지워진 뒤 멈춘다). 드릴이 실패하는지도 확인(R2 은닉 → FAIL). `docs/91` Q-51 해소 | 무중단 자율(테스트로 검증 가능, 되돌리기 쉬움) | scripts/offsite-*.sh·rollback-drill.sh, core/db/rollback/R2__*.sql, .github/workflows/ci.yml |
| 2026-07-09 | **PUR 웹 슬라이스(M5)를 앞당김 — M4-early와 같은 근거.** core에 `POST /purchases`·`GET /variants/{id}/purchases`가 이미 있고 web에만 화면이 없었다. **읽기+쓰기지만 core 변경 0·새 의존 0**이고, 되돌리려면 `web/src/purchase/` 삭제 + DecisionPage 1줄이다. 성적표 발급(REPORT_PENDING→CLOSED)은 손대지 않았다(core 도메인). 부수 확정: **날짜만 아는 구매 시각의 23:59 KST 환산은 입력 계층(web)의 책임** — V2 마이그레이션 주석의 계약을 코드로 옮겼고, 오프셋을 문자열에 박아 실행 머신 타임존에 의존하지 않는다 | 무중단 자율(테스트 GREEN, 되돌리기 쉬움) | web/src/purchase/, scripts/smoke.sh 5-2단계 |
| 2026-07-09 | **Q-27 상태변화 재처리(SOLD_OUT/DELETED → deal_event.ENDED) 배선.** Q-36으로 수집기가 `raw_deal_post`를 업서트하면서 품절이 원문엔 반영되나 `findUnprocessed`(링크 없는 글만)가 걸러 `deal_event`에 도달 못 하던 갭(BM-01 AC-2 절반) 복구. **종료 규칙 = 링크된 모든 원문이 종료됐을 때만 ENDED**(한 소스라도 ACTIVE면 여전히 구매 가능 — 재현율/정직성), last_seen은 종료 근거 시각으로 **단조** 갱신(뒤로 안 감). **격리 = 신규 `ReprocessDealStatusUseCase`**(별 관심사), 기존 `findUnprocessed`·`IngestDealsUseCase` **무수정** — 협업자 additive-only 규율 준수(수정은 `DealEventEntity.applyStatusChange`·`DealEventRepository.findByStatusIn` additive 메서드 2개뿐). 가격변화 재처리·효율 감지기·배치 오케스트레이션은 잔여(docs/91 Q-27) | 무중단 자율(사용자 방향 선택, Testcontainers GREEN) | core/application/ReprocessDealStatusUseCase, DealEventEntity·DealEventRepository, docs/91 Q-27 |
| 2026-07-09 | **무중단(Autonomous) 모드를 "스스로 계획하며 쭉 개발"로 강화 + 진행 로그 신설.** 증분마다 멈춰 "다음 뭐 할까"를 묻던 행동 제거 — 증분 우선순위는 자율 실행 결정이지 기획 결정이 아니다. 커밋 후 곧바로 남은 기획(roadmap·open-questions·planning-final·2nd-plan)을 읽어 다음 막히지 않은 최고가치 증분을 스스로 골라 이어간다. **되돌리기 쉬운 것(설계·seam·개발 의존·마이그레이션·additive)은 보수적 기본값+기록으로 자율**, 오직 하드 정지조건(데이터 파괴·외부 실발송/크롤/유료API·비용·보안·확정본 충돌·되돌리기 어려운 기획/데이터모델 결정·push)에서만 멈춘다. 되돌리기 어려운 결정도 `decisions-needed`에 적고 **다른 막히지 않은 일로 계속** 간다(전부 블록 시만 정지). 보고는 멈춤이 아니라 `working-area/progress-log.md`에 배치 매듭마다 append(durable). 하드 가드레일·절대원칙·확정본 권위·GREEN 후 커밋은 전부 보존 | 사용자 요청("진짜 중요한 거 말고 멈추지 말고 스스로 계획하며 무중단, 알릴 건 별도 MD에 차곡차곡") | CLAUDE.md §Autonomous·라우팅표, working-area/progress-log.md |
| 2026-07-10 | **core 파이프라인 트리거를 우리가 붙임 — 신규 파일만.** `ingestPending()`·`reprocessEndedDeals()`를 프로덕션에서 부르는 곳이 **한 군데도 없었다**(`@Scheduled`·`@EnableScheduling` 0건, `adapter/scheduler/`엔 `package-info.java`뿐). 즉 collector가 `raw_deal_post`에 써도 `deal_event`가 생기지 않아 **기준가 표본이 영원히 0**이었다 — M1을 막던 진짜 블로커는 토큰·키가 아니라 이것. 신규 `PipelineScheduler`(ingest→reprocess 순, 단계별 예외 격리, `initialDelay=interval`로 `@SpringBootTest` 오염 방지) + 신규 `SchedulingConfig`(`@EnableScheduling`). **기존 core 파일 수정 0** — `CoreApplication`·`application.yml`도 무수정. 테스트는 `core/src/test/resources/application.properties`로 전역 off, 배선 테스트만 되켬. 주기는 `core.pipeline.interval-ms`(기본 60s, compose env) | 사용자 승인("우리가 붙인다 — 신규 파일만") | core/adapter/scheduler/*, docker-compose.yml, scripts/smoke.sh 5-1b |
| 2026-07-10 | **가격 재처리의 도메인 규칙 확정**(Q-27 ①). `priceLast`("지금")의 증거는 **활성 원문뿐** — 방금 품절된 최저가를 "지금 가격"이라 말하면 살 수 없는 가격을 파는 셈이다. 같은 원문의 그 가격은 `priceMin`("지나간 기회")에는 남는다. `priceFirst`·`firstSeen`은 어떤 재처리에서도 불변(기준가 median·percentile이 그 위에 선다). `lastSeen` 단조. 변화 없으면 쓰지 않음. 스케줄러 순서 **ingest → 가격 → 종료**(종료가 마지막이라 닫히기 직전 가격까지 반영). 산술은 순수 `PriceRefresh`에 격리 — seam 1곳 | 무중단 자율(되돌리기 쉬움, 신규 파일만, 전 케이스 테스트) | core/domain/deal/PriceRefresh, core/application/ReprocessDealPricesUseCase, adapter/scheduler/PipelineScheduler |
| 2026-07-10 | **무중단이 끊기던 원인 제거 — 지침 개정.** 실측: 이번 세션 `ExitPlanMode` 11회 + `AskUserQuestion` 12회 = 23번 강제 정지. ① **턴 규율 신설**: "보고는 멈춤이 아니다"는 턴 기반 도구에서 거짓이므로 삭제하고, 턴 종료 조건을 셋으로 열거(정지조건/전부 막힘/컨텍스트 한계). 상세 보고는 `progress-log`에, 채팅은 턴 마지막 1회. ② **질문 규율**: `AskUserQuestion`은 정지조건에만(도구 이름으로 못박음). ③ **정지조건 정밀화**: "보안 정책 변경" → "보안 게이트를 **넓히거나 끄는** 변경"(좁히기·버전 핀·새 검사 도입은 자율). ④ **모듈 소유권 절 신설**: core=상대, 나머지=우리. core는 **신규 파일 additive만 자율**(`git status core/`로 무수정 증명), 기존 파일 수정은 보드에 적고 **우회**. ⑤ Git 규칙 현실화: "기능 단위 브랜치" 삭제(10/10 main 직접), "임의 머지 금지" → "사용자 지시 시에만"(`git pull`도 머지다). **정지조건 자체는 무르게 하지 않았다** — 데이터 파괴·외부 실발송·비용·push·확정본 충돌 그대로 | 사용자 승인(진단 + 개정안) | CLAUDE.md |
| 2026-07-10 | **`git push` 제한을 끄지 않고 좁힌다.** `permissions.deny`에서 `Bash(git push *)` 제거 → 일반 푸시 허용(단, `allow` 미등재라 매번 승인 프롬프트). **force-push·원격 참조 삭제**(`--force`/`-f`/`--force-with-lease`/`--delete`/`-d`/`--mirror`/`--prune`/`+refspec`/`:refspec`)는 `guard.sh`가 차단. **에이전트는 사용자 지시가 있을 때만 푸시**하고 무중단 모드에서도 자동 푸시하지 않는다. deny에 인자 패턴을 쓰지 않는 이유 = 공식 문서가 "fragile하니 PreToolUse 훅을 쓰라"고 권함(두 곳에 두면 어느 쪽이 진짜인지 모른다). 곁: 훅 matcher가 `"Bash"`뿐이라 **`PowerShell` 도구가 게이트를 통째로 우회하고 있었다** → `"Bash\|PowerShell"`로 좁힘 | 사용자 결정(범위 2택: "일반 푸시만" + "지시할 때만") | `.claude/settings.json`, `.claude/hooks/guard.sh`(+`.test.sh` 차단13/통과9), CLAUDE.md §Git·§Autonomous, docs/99 |

## 2026-07-10 — `배송비미상` 표식의 정본은 collector, core는 사본

- **결정**: 배송비 미상 판정은 파서가 아는 사실이므로 정본을 `collector/src/collector/pipeline/price.py: SHIPPING_UNKNOWN`에 둔다. core는 `domain/deal/DealTags.java`에 사본을 두고 DB 너머에서 읽는다.
- **왜 사본을 두나**: 계약이 DB(`deal_event.applied_conditions`)를 건너가므로 core가 문자열을 알아야 센다. 공유 스키마 파일을 만들 만큼 크지 않다(값 하나).
- **드리프트 방어**: `scripts/check-tag-contract.sh`(CI `lint`). 사본 쪽 테스트는 자기일관적이라 드리프트를 못 잡는다는 것을 뮤테이션으로 확인했다.
- **되돌리기**: 표식을 바꾸려면 DB의 기존 배열도 마이그레이션해야 한다(게이트는 리터럴만 본다). `DealTags` javadoc에 명시.

## 2026-07-10 — 무중단 턴 종료 조건에서 "컨텍스트 한계"(③)를 삭제한다

- **결정**: `CLAUDE.md` Autonomous 모드의 턴 종료 조건을 **셋에서 둘로** 줄인다. 삭제된 것은 ③ "컨텍스트가 한계에 가깝다".
- **왜**: 이 도구의 시스템 프롬프트가 정반대를 말한다 — *"When the conversation grows long, some or all of the current context is summarized; the summary … is provided in the next context window so work can continue — **you don't need to wrap up early or hand off mid-task.**"* CLAUDE.md는 "OVERRIDE any default behavior"로 주입되므로 에이전트는 ③을 따랐고, 실제로 그 문장으로 턴을 끝냈다("컨텍스트가 한계에 가까워 이번 배치를 마칩니다"). 사용자는 세션마다 "이어서 진행하자"를 열 번 넘게 쳐야 했다.
- **더 근본적으로**: ③은 **검증 불가능한 자기판정**이다. 남은 컨텍스트를 에이전트가 정확히 알 수 없으므로 언제든 발동할 수 있는 **만능 탈출구**였다. 저장소 자신의 규칙대로다 — *문서와 실행되는 계약이 모순이면 실행되는 쪽이 진실이다.*
- **함께 고친 것**: ① "배치 매듭이면 보고"·"채팅 보고는 턴 마지막에 한 번" — 도구 호출 없는 메시지가 곧 턴 종료이므로 이 의식은 **보고를 쓰는 행위 자체를 종료 행위로** 만들었다. ② "일감이 없다"(②)에 4단계 탐색 절차를 달았다(절차 없는 조항은 두 번째 탈출구가 된다). ③ 기계적 규칙 신설: **정지조건이 아니면 모든 메시지는 도구 호출을 포함한다.**
- **강제 장치**: 지침은 실행 가능한 계약이 아니라 강제할 수 없다. 그래서 **위반을 보이게** 만들었다 — 턴을 끝낼 때 `progress-log`에 `TURN-END: ①|② …` 마커를 남긴다. `grep 'TURN-END' working-area/progress-log.md`로 사후 감사한다. 컨텍스트를 사유로 든 항목이 하나라도 있으면 위반이고, 마커 없는 종료도 위반이다.
- **되살리지 말 것**: ③이 "안전장치"처럼 보이면 이 항목을 다시 읽어라. 컨텍스트가 차면 하네스가 요약한다. 요약 뒤의 나를 위한 장치는 `progress-log`이지 턴 종료가 아니다.
- **지침으로 못 고치는 것**: plan mode(Shift+Tab)가 켜지면 편집·커밋이 금지되고 `ExitPlanMode` 승인 왕복이 강제된다 — 무중단은 지침과 무관하게 끊긴다. `.claude/settings.json`에 `defaultMode`는 없다(세션 토글). 무중단 중 `EnterPlanMode`를 자발적으로 호출하지 않는다는 조항만 추가했다.

## 2026-07-10 — `배송비미상` 표식의 사본이 셋이 됐다 (collector 정본 + core + web)

- **결정**: web(`review/present.ts`)이 표식을 직접 비교하므로 사본이 하나 늘었다. `scripts/check-tag-contract.sh`를 **세 모듈**로 넓혔다.
- **왜 web에도 리터럴이 필요한가**: 화면이 `배송비미상`일 때만 "실제 결제가는 더 높습니다"를 덧붙인다. 그 판단을 core가 대신 보내 줄 수도 있지만(`isLowerBound` 불리언), 그건 `BenchmarkView` 계열의 계약 변경이라 지금은 사본이 싸다.
- **드리프트 시 증상**: core가 어긋나면 오염률이 **0으로 보인다**. web이 어긋나면 하한 경고가 **조용히 사라진다** — 가장 눈에 안 띄는 실패다. 그래서 게이트에 web 전용 차단 케이스를 넣었다.
- **사본이 또 늘면**: 게이트에 추가한다. **게이트가 모르는 사본은 게이트가 지켜 주지 않는다.**
## 2026-07-11 — core 모듈 소유권 조율: core 기존 파일도 우리가 무중단으로 수정

- **결정**: `core/`의 기존 파일도 우리가 무중단으로 수정한다. CLAUDE.md의 "core=상대 개발자" 소유권 조항은 문서로 남기되, **이 세션부터 상대와 조율됨**. web 프론트 UI만 사용자가 직접 지휘(지시 대기).
- **주체/근거**: 사용자 지시 — "core쪽 상대 개발자가 개발하던것도 그냥 뺏어와서 개발 무중단으로 진행해도돼 조율됐어 진행시켜". Q-27③ 한 줄 수정도 "우리가 해서 커밋"으로 명시 승인.
- **영향**: `git status core/` additive 증명 규칙은 이제 필수 아님(기존 파일 수정 허용). 단 **테스트 GREEN 후 커밋·기록 의무는 유지**. 첫 사례 = Q-27③(IngestDealsUseCase·AlertEvaluator·DealStatus·Reprocess 수정). web은 [[web-ui-wait-for-instruction]].
- **되돌리기**: 소유권은 언제든 다시 분리 가능(문서 조항이 정본). 실제 코드 변경은 기능 단위 커밋이라 개별 revert 가능.

## 2026-07-23 — 모듈 소유권 조항 자체를 폐기 (상대 개발자를 감안할 이유 없음)

- **결정**: CLAUDE.md의 `## 모듈 소유권 (동시 작업 충돌 방지)` 조항을 폐기하고 "전 모듈을 우리가 담당한다"로 대체한다. 2026-07-11이 "core도 우리가 수정"까지 열었으나 조항 문구는 남겨 뒀는데, M1·M2 내내 core 기존 파일을 계속 고쳐 조항이 실질과 완전히 어긋났다. 이제 상대 개발자를 감안할 이유가 없다.
- **주체/근거**: 사용자 지시 — "claude.md 수정하자 모듈 소유권에 대해서 상대 개발자를 굳이 감안할 이유가 없어짐".
- **영향**: CLAUDE.md 모듈 소유권 섹션 재작성(자율 기준 = "누구 것인가"가 아니라 "되돌릴 수 있는가"). force-push 금지 이유를 "남의 커밋"→"원격 이력 보호"로. `.claude/rules/core-java.md`의 "상대 소유"·"남의 엔티티" 표현 정리. 낡은 메모리 `counterpart-owns-core.md` 삭제 + MEMORY.md 인덱스 갱신. **Flyway는 여전히 core 단독 생성(충돌 방지, 소유권 아님)**. docs/91의 과거 "상대와 조율" 재개 트리거들은 로그라 그대로 두되, 이제 그 조율 조건은 자동 충족(막힘 아님).
- **되돌리기**: 문서 조항이라 언제든 복원 가능. 코드 영향 없음.

## 2026-07-25 — M6(WATCH·PRI) 착수 = WATCH 유보 최종 해제

- **결정**: `docs/30` M6는 "배치 착수 시 유보 해제 확정"이라 써 뒀다. M5(SIG·PUR·DIGEST) 완료 후 사용자가 "이어서 추천해주는걸어 무중단 개발 ㄱㄱ"로 M6 착수를 지시했다 — 이 착수 자체가 DN-W(WATCH 유보) 최종 해제다. 별도 확인 절차를 새로 만들지 않는다(로드맵 문구가 이미 트리거를 "착수"로 정의해 뒀다).
- **주체/근거**: 2026-07-08 일괄 승인(DN-W 개념 채택, decision-log)에서 이미 "실제 배치 = M6"만 남겨 뒀고, 2026-07-25 사용자가 M6 진행을 명시 지시.
- **영향**: `docs/17-feature-watchlist.md`의 "골자(유보 해제 시 확정)" 이하 전부가 이제 확정 대상이다. 착수 순서는 PRI(저비용, 완료) → WATCH DN-C1(DealEvent 재개 전이, 선행 의존) → WatchItem 본체.
- **되돌리기**: 문서 조항 갱신뿐 — 코드 영향 없음. WATCH 자체를 되돌리려면 별도 결정 필요(추적 개념 폐기는 정책 결정).

## 2026-07-28 — WATCH [샀어요] → PUR 프리필 = 판단 화면 이동 + 폼 프리필(A안), 실지불가는 딜 가격 + 경고

- **결정**: `docs/17` "[샀어요]→BOUGHT(PUR 프리필)"(Q-83 ②)의 모양을 사용자가 선택지 4안 중 A안으로 확정.
  - **A안(채택)**: 핀은 BOUGHT로 전이하고, 그 딜의 variant **판단 화면으로 이동**해 이미 있는
    `PurchasePanel` 폼을 채운 채 연다. 사람이 실지불가를 고쳐 [기록]을 누른다.
  - **실지불가 칸**: 딜 가격(`priceLast`)을 **미리 채우고**, 값 옆 안내는 **딜의 조건 태그로 가른다**
    (빈칸+참고표시 대신). CLAUDE.md "값을 못 구하면 그 사실을 값 옆에 실어 보낸다"와 같은 형태.

    | `applied_conditions` | 문구 |
    |---|---|
    | `배송비미상`(`price.py` `SHIPPING_UNKNOWN`) | ⚠️ 배송비를 못 읽어 **하한**입니다 — 실제 낸 금액으로 고치세요 |
    | 카드할인류(`카할` 등) | ⚠️ '카드할인' 조건이 붙은 가격입니다 — 그 카드로 안 샀다면 고치세요 |
    | 없음 | 이 딜의 관측가(배송비 포함)입니다. 쿠폰·적립을 따로 썼다면 고치세요 |

    **⚠️ 최초 확정 시엔 단일 문구("배송비·쿠폰 반영해 고치세요")로 적었으나, 같은 날 검증에서 그 전제가
    틀렸음을 확인해 정정한다** — `collector/src/collector/pipeline/price.py`가 `headline_price = main +
    shipping`으로 내므로 **딜 가격은 이미 배송비 포함**이다(BM-02 저장 기준 "실결제가 + 배송비"). 항상
    뜨는 경고는 안 읽힌다. 그리고 시스템은 "이 가격이 하한인가"를 이미 안다 — `SHIPPING_UNKNOWN`·카드할인
    표식이 `applied_conditions`에 실려 `DealEventEntity.appliedConditions`까지 도달한다.
  - **"채운다"를 유지하는 근거(검증)**: `paidPrice`는 **기준가 표본에 안 들어간다**(소비처 =
    `ReportCardCalculator`·`ObservationContextCalculator`·`PurchaseTriggers`·`Snapshot`) — 틀린 값의
    오염 범위가 내 성적표·내 알림 문턱으로 갇히고 공유 진실인 기준가는 안 다친다. 그리고
    `PurchaseTriggers`가 `dealPrice < paidPrice`로 "산 뒤 더 싼 딜"을 알리므로 paidPrice가 실제보다
    **낮으면 알림이 덜 온다 = 놓침**. 절대 원칙 3("놓침 > 오알림")상 포함가(높은 쪽)로 채우는 게 맞다.
  - **연결 딜**: `linkedDealEventId`에 핀한 딜을 자동으로 잇는다 — 지금 `buildPurchaseCommand`가
    "화면에 딜 연결 입력이 없다"며 항상 `null`로 보내는 죽은 필드가 이걸로 살아난다.
  - **수요축 값**: 프리필하지 않는다. SPLIT 제품이면 판단 화면에서 색을 고르는 기존 흐름을 그대로 탄다
    (딜의 `demand_axis_value`는 "미상"일 수 있고, 판단 화면에서 고른 색과 어긋나면 다른 색으로 기록된다).
- **탈락안과 이유**:
  - B안(보관함 안에서 인라인 폼): 판단 문맥(신호등·기준가)이 안 보이고, SPLIT이면 수요축 값을 여기서
    또 물어야 해 판단 화면에서 고른 값과 어긋날 위험. 폼도 두 벌이 된다.
  - C안(서버가 Purchase 자동 생성): **딜 가격 ≠ 실지불가**인 경우(쿠폰·적립·`배송비미상` 하한·카드할인
    미보유)를 시스템이 판별 못 한 채 성적표까지 흘려보낸다 — 사람만 아는 값을 시스템이 지어내는 꼴이다.
    미분류 딜(variant null)·SPLIT 수요축 미상이면 400으로 조용히 실패하고, 되돌리려면 구매 기록을 지워야
    한다. (검증 후 보정: 오염 범위는 기준가가 아니라 성적표·알림 문턱으로 갇히므로 위 서술의 "기준가를
    망친다"는 과장이었다. 그래도 **되돌리기 어려움 + 사람만 아는 값을 지어냄**이라 탈락 판단은 유지.)
  - D안(연결 딜 손잡이만): 가장 작지만 [샀어요]와 구매 기록이 여전히 안 이어져 docs/17 골자가 미착수로 남는다.
- **주체/근거**: 사용자 결정(2026-07-28, 선택지 제시 후 A안 + "딜 가격 채우고 경고 문구" 선택).
  같은 날 사용자 요청으로 재검증 → 안내 문구를 조건 태그 3분기로 보완(사용자 선택).
- **범위 밖으로 둔 것**(사용자 결정): "BOUGHT인데 구매 기록 없음" 불일치 표시(핀 전이와 기록은
  원자적이지 않다 — 이 약점은 A·B·D안 공통, 감수), 기록 후 보관함 복귀 링크(실사용해 보고 판단).
- **되돌리기**: web 표시 계층 + 얇은 응답 필드라 되돌리기 쉽다. seam = `PurchasePanel`의 프리필 prop 1곳.
- **영향(구현은 아직 안 함 — 사용자 지시로 착수 대기)**: `ResolvePinUseCase.markBought` 반환값(프리필 재료:
  variantId·dealEventId·딜 가격·appliedConditions), `WatchController` bought 응답, `App.tsx` 화면 이동 배선,
  `PurchasePanel`·`buildPurchaseCommand`(linkedDealEventId), `purchase/present.ts`(`prefillNotice` 3분기 +
  `price.py` 표식 정본↔web 사본 드리프트 게이트), `docs/91` Q-83 ②. 계획 파일:
  `~/.claude/plans/melodic-sparking-micali.md`.

## 2026-07-30 — Q-83 잔여 5건(①③④⑤) 설계 일괄 확정 — 이후 무중단 구현

사용자 요청("사람의 판단이 필요한 영역에 대해서 전부다 지금 정하고 가자")으로 Q-83 잔여 전체를 코드
추적 후 판단 필요 지점만 추려 확정. ②(PUR 프리필)는 이미 이 세션 앞부분에서 구현 완료.

### ① anchorPostId 자동 승계 — 자율 결정(되돌리기 쉬움, 묻지 않음)
- **맥락 정정**: 처음엔 "새 DealEvent가 옛 딜의 재구성임을 매칭하는 휴리스틱"이 필요하다고 오해했으나,
  코드 추적 결과 REOPENED(부활)는 **같은 dealEventId를 재사용**해 WatchItem의 FK가 애초에 안 끊긴다.
  실제 "재구성"은 `IngestDealsUseCase`가 새 원문을 **기존 dealEventId**에 병합할 때
  `DealEventSourceEntity(existing.getId(), post.getId(), site)`를 추가하는 순간이다 — `PinDealUseCase`가
  핀 시점에 소스 중 하나를 `anchorPostId`로 고정해 두는데, 그 뒤 병합으로 새 소스가 붙어도 안 갱신된다.
- **결정**: 그 병합 지점에서 해당 dealEventId에 **ACTIVE WatchItem이 있으면 anchorPostId를 방금 병합된
  새 소스로 갱신**한다(최신 원문이 항상 앵커). "+이력"은 별도 이력 컬럼을 추가하지 않는다 — 과거 앵커가
  뭐였는지는 `deal_event_source` 테이블에 전부 남아 있어 필요하면 거기서 재구성 가능하다(사본 없음).
- **근거**: 되돌리기 쉬움(FK 값 하나, 다음에 정책 바꿔도 마이그레이션 불필요) — CLAUDE.md 자율 진행 기준.

### ③ 핀 이력 딜 사후학습 제외 — 자율 결정(되돌리기 쉬움, 묻지 않음)
- **맥락**: BM-07 사후학습(`IgnoreDealUseCase.suggestKeywords`)은 `deal_ignore`(Telegram [🔕무시] 클릭)
  테이블의 제목들에서 빈출 토큰을 뽑는다. 핀(WATCH)과 무시(Telegram 무시 버튼)는 서로 다른 표면의 독립
  액션이라, 같은 dealEventId가 이론상 둘 다에 들어갈 수 있다 — 그러면 "사람이 원해서 지켜본 딜"의 제목이
  "노이즈"학습 재료로 쓰인다.
- **결정**: `suggestKeywords`가 `ignoredTitles`를 모을 때 **WatchItem이 존재(상태 무관)하는 dealEventId는
  제외**한다(Q-83이 이미 쓰는 "핀 이력 딜" 정의 그대로 재사용 — 사본 아님). 신규 리포지토리 메서드
  `WatchItemRepository.existsByDealEventId(long)` 하나만 추가.

### ④ 핀 후속 특례(인상 1회) — 사용자 결정
- **선택지**: (A) 인상만 핀 기반으로 열기(첫 알림 무관, 신규 FollowUpKind 하나만 추가·기존 게이트 불변)
  vs (B) 핀되면 인하·품절·종료·검증까지 전부 첫 알림 무관 무조건(기존 `FollowUpEvaluator.alreadyAlerted`
  게이트 자체를 변경 — 블라스트 반경 큼, 이미 배선된 알림 발화 빈도가 전 핀 딜에 늘어남).
- **채택**: **(A)**. 새 `FollowUpKind`(가칭 `PINNED_PRICE_INCREASED`) 하나만 추가하고, 대상은 **ACTIVE
  상태의 WatchItem**에 한정(BOUGHT·MISSED·DROPPED로 결말난 핀은 이제 와서 가격 인상을 알려도 무의미).
  가격 증가 방향 판별은 `ReprocessDealPricesUseCase`가 이미 갖고 있는 이전/이후 가격 비교에서 파생.
  "1회만"은 기존 `(dealEventId, kind)` 유니크 이력 패턴을 그대로 재사용 — 새 메커니즘 불필요.
- **탈락(B) 이유**: 원문을 글자 그대로 읽으면 B가 더 가깝지만, 기존에 이미 동작 중인 VERIFIED·ENDED·
  PRICE_CHANGED(방향 무관, 이미 "인상 무조건"을 알림 나간 딜에는 충족)의 발화 게이트 자체를 바꾸는 건
  회귀 위험이 크고, "핀 = 알림 자격 부여"라는 새 개념을 여러 알림 종류에 동시에 얹는다. A는 새 알림
  1종만 추가하는 좁은 변경이라 되돌리기 쉽고 검증 범위가 작다.

### ⑤ 부활 미응답 플래그 — 사용자 결정
- **해소 방식**: 명시적 **[확인함] 버튼**(WatchPage 회고 또는 활성 탭)을 채택 — 이 앱의 기존 패턴
  (샀어요/기각·해제처럼 사람이 명시적으로 누르는 버튼)과 일관되고, "봤다"를 스크롤·렌더링으로 추정하는
  쪽보다 테스트하기 쉽고 정직하다(절대 원칙 2: 판단은 사람).
- **범위**: **부활 미응답 플래그만 최소로** 구현한다. 2nd-plan-intake의 "미열람 3종"(인하/확인필요-부활대체/
  놓침확인) 통합 대시보드는 이번에 만들지 않는다 — WatchItem에 필드 여러 개 + WatchPage 배지 체계 재설계가
  필요해 범위가 커진다. 지금은 REOPENED가 ACTIVE 핀에 발생했을 때 `WatchItemEntity`에 불리언 플래그
  하나(`reviveUnacknowledged`)를 세우고, REST로 확인 처리(false로 되돌림)하는 좁은 슬라이스만 만든다.
- **자격 상실 확인 필요 알림**(항목 ⑤의 나머지 절반)은 별도 결정 없이 진행 가능 — 새 `FollowUpKind`
  추가로 충분(④와 같은 메커니즘). ACTIVE 핀의 dealEventId가 `ResolveReviewItemUseCase`/
  `ReviewCallbackRouter`를 통해 outlierFlag NONE→비NONE 전이할 때 발화, 핀 상태 전이는 없음(그대로 ACTIVE).

### 공통
- **주체**: ①③은 CLAUDE.md 자율 진행 기준(되돌리기 쉬움)으로 AI가 결정. ④⑤는 AskUserQuestion으로
  선택지 제시 후 사용자가 확정(2026-07-30).
- **되돌리기**: 다섯 항목 전부 좁은 seam(신규 컬럼 1개·신규 FollowUpKind 값·리포지토리 메서드 1~2개)이라
  개별적으로 되돌리기 쉽다.
- **영향**: `PinDealUseCase`/`IngestDealsUseCase`(①), `IgnoreDealUseCase`(③), `FollowUpKind`·
  `FollowUpAlertUseCase`·신규 유스케이스(④⑤), `WatchItemEntity`(⑤ 플래그 컬럼), `WatchController`/
  `WatchPage`(⑤ 확인 버튼), `docs/91` Q-83.

## 2026-07-30 — Q-48 ② 수요축 필터(alert_policy.demand_axis_filter) 매핑 — 자율 결정(되돌리기 쉬움)

- **맥락**: docs/91 Q-48이 오래 열려 있던 항목 — "이 variant에서 어느 축값만 알림 받을지" 필터. 컬럼은
  V1부터 jsonb로 있었으나 "소비 기능과 함께 매핑한다"는 원칙(K_display·제외 키워드의 선례)에 따라 계속
  미뤄져 있었다. 이번에 소비처(`EvaluateAlertOnDealUseCase`)와 생산자(`AlertPolicyPanel`)를 함께 배선.
- **결정 1(저장 표현)**: `AlertPolicyEntity.demandAxisFilter`를 `List<String>`이 아니라
  `Map<String,Object>`(키 `"values"`)로 매핑 — `exclude_keywords`(`List<String>`+`SqlTypes.ARRAY`)와
  같은 엔티티에 `List<String>`+`SqlTypes.JSON`을 또 붙이면 Hibernate가 스키마 검증에서 서로의 타입을
  잘못 요구하는 실측 버그를 만났다(`docs/99-lessons` 2026-07-30). `String[]`도 다른 오류로 실패해
  `ReviewQueueItemEntity.payload`가 이미 쓰는 `Map<String,Object>`+JSON 조합으로 우회— 바깥 API는
  여전히 `List<String>`(변환은 `AlertPolicyEntity.toColumn()` 한 곳).
- **결정 2(필터 의미론)**: 빈 목록 = 필터 없음(전 축값 알림, 기존 동작과 동일 — 가장 보수적 기본값).
  값이 있으면 **allowlist**(그 값들만 알림). 첫 알림 자격(`EvaluateAlertOnDealUseCase`)의 SPLIT+값
  미상 스킵 바로 다음에 검사 — 값 미상 딜은 이미 그 앞에서 걸러지므로 필터는 "값이 있는데 안 맞는"
  경우만 본다.
- **결정 3(web UI 재료)**: `AlertPolicyPanel`이 축의 이름·허용값을 새로 조회하지 않고 `DecisionPage`가
  이미 계산해 둔 `demandAxis`를 prop으로 받는다 — 판단 화면이 이미 그 자리에서 계산해 둔 것을
  재사용(추가 API 호출 없음). GROUPED 제품(`demandAxis === null`)이면 이 fieldset 자체를 안 그린다
  (없는 손잡이는 안 그린다).
- **주체**: AI 자율 결정 — 셋 다 되돌리기 쉬운 seam(컬럼 저장 표현 1곳, 필터 의미론 1개 조건문,
  prop 전달 1곳)이라 CLAUDE.md 자율 진행 기준에 해당. 코드 추적으로 근거를 확인했다(Hibernate 오류
  재현, DecisionPage의 demandAxis 계산 위치 확인).
- **영향**: `AlertPolicyEntity`·`AlertPolicySettings`·`AlertPolicySettingsUseCase`·`AlertPolicyController`·
  `EvaluateAlertOnDealUseCase`(core), `AlertPolicyPanel`·`DecisionPage`·`buildPolicyCommand`(web),
  `docs/91` Q-48, `.claude/rules/web-react.md`, `scripts/dead-columns-allowlist.txt`(낡은 면제 제거).

## 2026-08-06 — CLAUDE.md 교훈 승격 2계층 → 3계층 개편 (`## 축적된 규칙` 41개 → 17개)

- **맥락**: 사용자가 "CLAUDE.md가 너무 길다"고 지적. 공식 Claude Code 가이드(code.claude.com/
  best-practices, /memory) 확인 결과 실제 문제였다 — 권장 60~200줄·상한 ~300줄인데 실측 209줄·
  41,649바이트(줄당 199바이트, 영문 표준의 2.5~3배 밀도). `## 축적된 규칙`(41개 불릿)이 전체
  바이트의 47%(19,447바이트)를 차지 — 대부분이 "규칙(굵게) + 이유 + 실측 사례" 3~6문장 문단으로,
  `docs/99-lessons.md`의 서사를 그대로 인라인 확장해 둔 것에 가까웠다.
- **결정**: 2026-07-09 도입한 2계층 승격(CLAUDE.md 축적된 규칙 / `.claude/rules/<scope>.md`)을
  **3계층**으로 개편 — ③ `docs/21-tdd-guidelines.md`(테스트 작성 기법·결함 패턴, 온디맨드)을
  추가하고, CLAUDE.md는 "언어 무관 + 정말 매 세션 필요"만 남긴다. 41개 불릿을 감사해 17개만
  CLAUDE.md에 압축 유지(1~2문장), 16개는 이미 있던 `.claude/rules/*.md` 4개 파일로(그중 7개는
  이미 거의 같은 문구로 중복 존재해 이관 없이 CLAUDE.md 쪽만 삭제, 7개는 신규 작성), 8개는
  `docs/21`의 새 절 "## 테스트 결함 패턴"으로, 1개(기계화 여부 질문)는 "## 교훈 축적 프로토콜"
  절로, 1개(복구 절차 리허설)는 `## 빌드·테스트 명령`과 순수 중복이라 삭제만 했다.
  **`session-brief.sh`의 드리프트 지표**도 3곳(CLAUDE.md·`.claude/rules/*`·`docs/21`) 합산으로
  갱신 — 하나만 세면 나머지 두 곳으로 흩어진 승격을 "누락"으로 오판한다.
  **CLAUDE.md 자체를 고칠 때 참고할 요약 가이드**(`.claude/rules/claude-md-editing.md`, `paths:
  ["CLAUDE.md"]`)를 신설 — 공식 가이드 재조회 없이 매번 참고하도록.
- **결과**: 183줄·27,354바이트(41,649→27,354, 34% 감소). AGENTS.md(Codex 미러, git 미추적)는
  사용자 결정으로 이번 범위 제외.
- **주체**: 사용자 요청(조사 후 확정) — plan mode로 계획 승인 후 실행.
- **영향**: `CLAUDE.md`, `.claude/hooks/session-brief.sh`, `docs/21-tdd-guidelines.md`,
  `.claude/rules/{core-java,collector-python,web-react,shell-scripts,claude-md-editing}.md`,
  `collector/tests/{test_scheduler,test_observability}.py`(docstring 인용 갱신).


## 2026-08-07 — D-8 PUR 자동 아카이브: 지금은 안 만든다

- **맥락**: `docs/15` PUR-06이 전제하는 "다른 활성 관찰 없으면 자동 CLOSED→ARCHIVED"가 미구현
  (Q-62 잔여). 수동 아카이브(Q-86, `POST /purchases/{id}/archive`)는 이미 배선돼 있어 사람이
  원할 때 언제든 접을 수 있다 — 자동화는 순수 편의 기능이라 급하지 않았다.
- **결정**: 사용자가 `AskUserQuestion`으로 "지금은 안 만든다"를 선택(권장안 채택). 스코프(variant
  전체 vs demandAxisValue 단위)·트리거 시점(즉시 vs 배치)·Q-30(삭제 매트릭스)과의 정합 문제는
  전부 미착수 상태로 유지.
- **주체**: 사용자 결정(D-8 확정).
- **영향**: `docs/91` Q-62(잔여 그대로 유지, 재개 트리거 불변), `working-area/decisions-needed.md`
  에서 D-8 제거.

## 2026-08-07 — D-9 CMP-02 반자동 웹 폼: 폴백 자체를 만들지 않는다

- **맥락**: `docs/13` CMP-02가 명시하는 "확장 부재/고장 시 반자동 붙여넣기 웹 폼"이 2026-08-05
  재검증에서 아예 없다는 게 드러났다(Q-79). 막힌 지점은 인증 경계 — `X-Extension-Token`을
  웹 클라이언트에 심으면 시크릿 노출(정지조건)이라 세 선택지(별도 엔드포인트+nginx Basic Auth
  경계 / 배포 시점 토큰 주입 / 폴백 자체를 안 만듦) 중 하나를 사람이 확정해야 했다.
- **결정**: 사용자가 `AskUserQuestion`으로 "폴백 자체를 안 만든다"(권장안)를 선택. 크롬 확장
  본체도 여전히 fixture 대기 중(Q-79)이라 이 폴백이 없어도 `DecisionPage`의 "쿠팡 관측
  미확인" 정직한 표시로 정상 동작 — 인증 경계 설계에 드는 비용 대비 이득이 낮다고 판단.
- **주체**: 사용자 결정(D-9 확정).
- **영향**: `docs/91` Q-79(재개 트리거 ③ "D-9 결정 시 웹 폼 착수" 항목을 "D-9 확정: 폴백 자체를
  만들지 않기로 함 — 크롬 확장만 유효 경로"로 갱신), `working-area/decisions-needed.md`에서
  D-9 제거.

## 2026-08-07 — D-10 reaction_score: 이 범위에서 포기한다

- **맥락**: 확정본(`docs/90:58,187`)이 요구하는 반응 신호(reaction_score) 노출을 core가 한
  번도 구현하지 않았다(코드리뷰 20260806 X-03, `docs/91` Q-89). 노출하려면 사이트 간 정규화
  방식(뽐뿌·루리웹·펨코 추천수 체계가 다름, `docs/90:232`가 이미 미확정으로 적어 둠)부터 정해야
  구현이 시작되는데, 그 전 단계인 "노출 자체를 할지"가 먼저 막혀 있었다.
- **결정**: 사용자가 `AskUserQuestion`으로 "이 범위에서 포기한다"를 선택. 데이터는 collector가
  이미 정직하게 수집·적재 중이라(`raw_deal_post.reaction_score`) 유실은 없다 — 노출만 범위에서
  뺀다. 확정본 문구는 이 결정을 반영해 델타로 정정(아래 참조).
- **주체**: 사용자 결정(D-10 확정).
- **영향**: `docs/90-planning-final.md:58,187`에 v1.3 이후 델타 각주 추가(요구 철회, 문구 자체는
  기록 보존을 위해 남기고 각주로 정정 — 절 전체를 지우면 "왜 컬럼이 미배선인가"의 근거가 사라진다),
  `docs/91` Q-89를 [해소]로 전환(reaction_score는 계속 수집만 되고 영구히 노출 안 함),
  `working-area/decisions-needed.md`에서 D-10 제거. (`scripts/dead-columns-allowlist.txt`는 손댈
  것 없음 — `reaction_score`는 애초에 그 파일에 등록된 적이 없다. Q-89가 지적한 게이트 사각지대
  때문에 `check-dead-columns.sh`가 collector 쓰기 코드만 보고 "배선됨"으로 오판해 애초에
  DEAD로 안 잡혔고, 그래서 예외 등록 자체가 필요 없었다 — 이 사각지대는 D-10과 별개로 여전히
  남아 있다, 다른 컬럼이 같은 함정에 빠질 수 있다는 뜻.)
