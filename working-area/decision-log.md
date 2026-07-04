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
| 2026-07-04 | Claude Code 권한 = acceptEdits + 프로젝트 배시 허용목록(.claude/settings.local.json, gitignore). 파괴적 명령(rm 등)은 계속 승인 요구 — autonomous 정지조건과 정렬 | 사용자 요청(승인 팝업 과다) | .claude/settings.local.json |
| 2026-07-04 | **(D-2) Spring Boot 3.5.16 → 4.1.0 이관.** 스택 확정본의 "Boot 3.x 변경 금지"를 개정. **4.0.x가 아니라 4.1.0 채택** — 4.0.x는 2026-12-31 OSS EOL이라 D-2의 목적(임박 EOL 회피)과 모순, 4.1.0이 최신 안정판·최장 지원. M0 스캐폴드 저표면적 시점에 이관 | 사용자 결정(D-2, "최신 지원 확보 + 문서 반영") | core/build.gradle.kts, docs/90 §7, CLAUDE.md 스택, docs/99 |
| 2026-07-04 | Boot 4.1 이관 fallout 확정: web→webmvc 스타터 리네임, flyway는 spring-boot-starter-flyway(+flyway-database-postgresql 별도 유지), Testcontainers 2.0(아티팩트 testcontainers-* 접두사·패키지 org.testcontainers.postgresql·self-type 제네릭 제거) | Boot 4.1 BOM 실측(./gradlew test GREEN) | core/build.gradle.kts, TestcontainersConfiguration.java |
| 2026-07-04 | **docs/31 수치 파라미터 6개 승인**(운영자): MERGE_PRICE_TOLERANCE ±2%(min ±5,000), MERGE_WINDOW_HOURS 48, OUTLIER_IQR_MULTIPLIER 1.5, COLDSTART_JACKPOT_RATIO 0.30, K_DISPLAY 5(3~10), EXPAND_LIMIT_MONTHS 12. `BenchmarkParams.defaults()`로 상수화, 도메인은 주입 params만 참조. docs/91 Q-1 해소 | 사용자 승인(제안값 그대로) | docs/31, BenchmarkParams |
| 2026-07-04 | `BenchmarkParams`(수치 seam)를 `domain.benchmark` → **`domain` 루트로 이동**. benchmark·deal·matching 공통 참조 공유 커널이라, `benchmark→deal`(BenchmarkCalculator)와 `deal→benchmark`(DealMergePolicy) **패키지 순환**을 끊음. 같은 이유로 `Quantiles`도 domain 루트로 이동(+P75) | BM-04·05 착수 시 순환 발견 | core domain/BenchmarkParams.java·Quantiles.java |
| 2026-07-04 | collector 런타임 의존 **beautifulsoup4** 추가 — 루리웹·펨코 HTML 파서(번개는 JSON, stdlib). krepe90 골격도 bs4 사용. `uv add` → pyproject·uv.lock 갱신 | BM-01 파서 착수 | collector/pyproject.toml, uv.lock |
