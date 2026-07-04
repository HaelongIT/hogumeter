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
