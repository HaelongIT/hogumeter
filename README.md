# hogumeter (호구미터)

[![CI](https://github.com/HaelongIT/hogumeter/actions/workflows/ci.yml/badge.svg)](https://github.com/HaelongIT/hogumeter/actions/workflows/ci.yml)

> **내가 사려는 물건의 "핫딜 기준 적정가"를 커뮤니티/핫딜 데이터로 산출하고, 현재 판매가가 그보다 얼마나 비싼지(갭)를 제시해 "지금 사도 호구가 아닌지"를 판단하게 돕는 1인용 구매 의사결정 보조 시스템.**

핵심 지표는 **정가가 아니다.** 상시할인 때문에 정가는 무의미해서 공식 폐기했다. 대신 커뮤니티 핫딜 분포로 만든 **핫딜 기준가**와, 현재가와의 차이인 **갭(gap)** 을 본다.

```
현재가 ─────────────── 갭 ──────────────▶ 핫딜 기준가(median)
890,000원                                     820,000원
                                       "지금은 기준가보다 7만원 비쌈 — 조금 더 기다려도 됨"
```

---

## 무엇을 하나 (기능)

**신품 코어** — 시스템의 심장은 기준가 엔진이다.

| 기능 | 하는 일 | 상태 |
|---|---|---|
| **등록 (REG)** | 제품명 → variant 축 정의 → 별칭 시드 · 알림 정책(목표가·기간·quiet hours·K·제외 키워드·수요축 필터) | ✅ REST + 웹 화면 동작(등록·정책 둘 다). ⛔ 네이버 후보 검색은 **API 서비스 자체가 종료**돼([Q-3](docs/91-open-questions.md)) 수동 입력만 — 대체안은 [D-7](working-area/decisions-needed.md) 결정 대기 |
| **수집 (collector)** | 핫딜 3사 + 번개장터 폴링 → 파싱 → 가격 정규화 → `raw_deal_post`/`listing` 업서트 | ✅ 전 구간 동작(robots 존중·레이트 하한·백오프·차단 중지·드리프트 감지, 4사 전부). ⛔ 실 폴링은 `COLLECTOR_ALLOW_NETWORK=1` 필요(기본 off) — 사람 승인 사항 |
| **파이프라인 (core)** | `raw_deal_post` → 매칭 → `deal_event` → 가격 재처리 → 종료 판정 → 성적표 발급 (60초 주기) | ✅ `PipelineScheduler`. 매 틱 카운터(OBS-02) + JSON 로그(OBS-01). 스모크가 종단 증명 |
| **기준가 엔진 (BM)** | 병합·교차검증 → 이상치 양방향 분리(Tukey + SPARSE 현재가 폴백) → median 기준가 + P25 굿딜라인 + 3단 표본 표시 | ✅ 순수 도메인 + REST + 판단 화면. ⛔ 실 데이터 미유입이라 **운영자 체감 대조 미완**(M1 완료 기준). 현재가는 네이버 API 종료로 **미확립**이라 표시하고 갭을 그리지 않는다([Q-53](docs/91-open-questions.md)) |
| **감시·알림 (AL)** | 트리거 판정 → 텔레그램 알림(인라인 버튼 승격·기각) → 무시→키워드 사후학습 → 미상 큐(web·텔레그램 양쪽 승격 가능) | ✅ 판정·발송·인바운드 콜백 코드 전부 완성. ⛔ **봇 토큰 미발급**(Q-20)이라 `.env`를 채우기 전까지 실제로는 조용하다 |
| **구매 비교 (CMP)** | 살 결심 순간 판매처 실결제가 횡비교(네이버 + 쿠팡 확장) | 🔨 CMP-01 온디맨드 비교 부분 구현(판단 화면에 쿠팡 관측가 병기, ingest REST 완성) — 네이버 leg는 Q-3로 막힘, 크롬 확장 자체·반자동 폴백 폼은 미착수 |
| **중고 (USED)** | 3계층 키워드 매칭 + 목표가·생애주기 알림 + URL/텍스트/수동 3단 평가기 + 메모·비교표 | ✅ 등록·평가·비교 REST + 웹 화면까지 전부 배선 완료. ⛔ 실 폴링은 승인 사항(코드는 완성) |

**판단 보조 표면 (2차 기획)** — 신규 수집 0, 위 데이터의 재조합. "구매 이후 루프 + 판단 압축".

| 기능 | 하는 일 | 상태 |
|---|---|---|
| **구매 성적표 (PUR)** | 구매 기록 → 관찰 모드(산 뒤에도 알림) → 사후 "호구였나" 성적표 발급 | ✅ 기록·관찰 문맥·성적표 발급(REPORT_PENDING→CLOSED)까지 REST + 웹 화면 동작 |
| **신호등 (SIG)** | 🟢🟡🔴⚪ 한 칸으로 "지금 잡을 딜 있는가" 압축 | ✅ 완료 — 판단 화면에 통합 |
| **딜 주기 (CAD)** | "기다리면 또 오는가" — 발생 빈도·간격(예측은 안 함) | ✅ 완료 — 판단 화면에 통합 |
| **다이제스트 (DIGEST)** | 주간 요약 리포트(놓침 안전망, 매주 일요일 20시 KST) | ✅ 완료 — 6섹션(딜 요약·전환·관찰 경과·핀 결말·미상 큐·조용한 주) + 스케줄 발송 |
| **보관함 (WATCH)** | 알림↔구매 사이 "고민 구간" 핀·결말·회고, [샀어요]→구매 기록 프리필 | ✅ 완료 — 판단 화면 핀 버튼 + 딜 보관함 웹 화면. 잔여 1건은 구조적으로 발생 불가로 판정([Q-83](docs/91-open-questions.md)) |
| **우선순위 (PRI)** | 대기열 순서 상기(목록 정렬) | ✅ 완료 — 웹 화면(순번·수동완료 손잡이) |

> ✅ 구현 완료 · 🔨 부분 구현 · ⛔ 외부 요인(토큰·키·승인)으로 막힘. 상세 = [`docs/`](docs/), 실시간 상태는 [`docs/91-open-questions.md`](docs/91-open-questions.md)·[`docs/30-roadmap.md`](docs/30-roadmap.md)가 정본.

---

## 핵심 원칙 (모든 설계 결정의 잣대)

1. **정직성** — 표본이 빈약하면 통계 용어("기준가", "median")를 쓰지 않는다. 모든 기준가는 표본 수 `n건(교차 m건)` 을 상시 동반.
2. **판단은 사람, 시스템은 근거** — 사기·최종 구매 판단 로직을 만들지 않는다. 원문 링크·반응 신호·갭·위험 신호를 한자리에 모아줄 뿐.
3. **속도·재현율 우선** — 놓침 > 오알림. 애매하면 후보로 올려 사람이 1초 확인.
4. **손잡이 배분** — 표시를 바꾸는 설정은 사용자에게, 데이터 진실을 바꾸는 규칙은 시스템 고정.
5. **플랫폼 잣대** — 공식 API 우선 / 기술적 차단은 우회 금지(프록시·UA 위장 금지) / 차단 없는 공개 페이지는 저빈도·개인용 수용.
6. **과대약속 금지** — 자동으로 못 잡는 것은 솔직히 경계 긋고 원문 링크로 넘긴다.

---

## 로드맵

| 마일스톤 | 범위 | 상태 |
|---|---|---|
| **M0** | 스캐폴딩 · 수치 확정 · 스파이크 | ✅ 완료 |
| **M1** | 신품 코어 루프 (REG + BM + AL + 텔레그램) — 아이폰 17 256GB 1차 검증 | 🔨 코드 완성, 남은 블로커는 전부 외부(네이버 API 종료·봇 토큰·실 폴링 승인) |
| **M2** | 중고 (USED 전체) | ✅ 코드 완성(등록·수집·생애주기 알림·평가기·비교표), 실 폴링 승인 대기 |
| **M3** | 구매 비교 (CMP) + 크롬 확장 | 🔨 CMP-01(쿠팡 관측 병기) 완료, 네이버 leg는 M1과 같은 이유로 막힘, 크롬 확장 자체는 실 DOM fixture 대기 |
| **M4** | 웹 마감 (조회·비교·큐·설정) | 🔨 미상 큐·설정·이상치 토글 완료 — 남은 건 M2/M3 잔여와 겹친다 |
| **M5** | 판단 보조 표면 — SIG·CAD → PUR → DIGEST | ✅ 완료 |
| **M6** | 보관함(WATCH) · 우선순위(PRI) | ✅ 완료(WATCH 잔여 1건은 구조적으로 발생 불가) |

상세: [`docs/30-roadmap.md`](docs/30-roadmap.md). 기획 확정본(최종 권위): [`docs/90-planning-final.md`](docs/90-planning-final.md) (v1.3, 2차 통합 반영).

---

## 아키텍처

폴리글랏 — 수집(Python)과 코어(Java)는 **DB 테이블 계약으로만** 접합한다(직접 호출 없음).

```
[collector(Python)] --write--> [PostgreSQL 16] <--read/write-- [core(Spring Boot)]
                                                     |-- REST --> [web(React)]
                                                     |-- REST <-- [extension(Chrome, 기능4)]
                                                     |-- Bot API --> [Telegram]
```

- **core**: 헥사고날 — 순수 `domain`(기준가·이상치·매칭·알림 판정, IO/프레임워크 의존 0)을 얇은 `adapter`가 감싼다. 모든 판정은 "입력 → 결과" 순수 함수라 단위 테스트로 완결.
- **collector**: 파서는 네트워크와 분리된 순수 함수(`html → DTO`), fixture golden 테스트.
- **이식성**: Docker Compose · EC2 1대. AWS 관리형 서비스에 코어 로직을 얹지 않는다(Postgres 컨테이너 자기완결).

모듈 경계: [`docs/01-architecture.md`](docs/01-architecture.md) · [`core/README.md`](core/README.md) · [`collector/README.md`](collector/README.md).

---

## 스택

| 영역 | 기술 |
|---|---|
| core | Spring Boot 4.1 · Java 21 · Gradle(Kotlin DSL) · JUnit 5 · Testcontainers |
| collector | Python 3.12 · uv · pytest · BeautifulSoup |
| DB | PostgreSQL 16 (Flyway 마이그레이션은 core 단독 소유, JSONB는 크롤링 원본 보관 전용) |
| 알림 | Telegram Bot (인라인 버튼 승격) |
| web | React · Vite · TypeScript (등록·판단·구매기록·미상 큐·딜 보관함·중고·우선순위·설정) |
| extension | Chrome 확장 (쿠팡 리더, 기능4 단계) |
| 배포·CI | Docker Compose · AWS EC2 · GitHub Actions |

---

## 빠른 시작

```bash
# 0) 시크릿 준비 (.env는 gitignore — 절대 커밋 금지)
cp .env.example .env    # DB_PASSWORD 등 값 채움

# 1) 전체 기동 — http://127.0.0.1:3000 (web), :8080 (core)
docker compose up -d    # postgres + core + web + collector

# 2) 종단 스모크 (전용 프로젝트로 격리 — 개발 데이터 안 건드림)
bash scripts/smoke.sh   # 빌드 → 기동 → web → nginx → core → postgres 왕복 + Basic Auth

# 2-1) 복구 리허설 (REL-04 백업·복원 / REL-05 롤백)
bash scripts/backup.sh          # pg_dump + gzip → backups/, 7일 보관
bash scripts/restore-drill.sh   # 일회용 컨테이너에 복원해 검증 (운영 DB 안 건드림)
bash scripts/offsite-drill.sh   # 오프사이트 업로드 리허설 (MinIO — 실 AWS 미호출)
bash scripts/rollback-drill.sh  # 마이그레이션 롤백 리허설 (REL-05, 일회용 컨테이너)

# 3) 모듈별 테스트
cd core && ./gradlew test                    # 단위 + Testcontainers 통합
cd collector && uv run pytest                # 전체 (Docker 필요)
cd collector && uv run pytest -m "not integration"   # 빠른 루프 (~1초)
cd web && npm test && npm run build          # Vitest + 타입체크

# 4) (선택) 커밋 전 시크릿 스캔 훅 켜기 — SEC-01. 매 커밋 수 초 소요, CI에도 같은 게이트가 있다
git config core.hooksPath .githooks           # 끄기: git config --unset core.hooksPath
```

> 수집은 기본 비활성이다. `COLLECTOR_ALLOW_NETWORK=1`로 켜야 핫딜 3사에 실제 요청을 보낸다(정지조건의 기계적 강제). 켜기 전 [`pre-deploy-checklist`](working-area/pre-deploy-checklist.md) §F 확인.

- 로컬 시크릿은 루트 `.env`([`.env.example`](.env.example) 참조): DB 비밀번호 · 텔레그램 봇 토큰 · 네이버 쇼핑 API 키.
- **텔레그램 연동은 어댑터가 완성돼 있다** — `.env`에 `TELEGRAM_ENABLED=true`+토큰+chat id를 채우면 즉시 알림·인라인 버튼이 산다([`docs/91`](docs/91-open-questions.md) Q-20). **네이버 연동은 코드가 아니라 데이터 소스 자체가 막혀 있다** — 네이버가 해당 검색 API를 종료했다(Q-3, [D-7](working-area/decisions-needed.md) 대체안 결정 대기).
- 보안·운영 주의: [`SECURITY.md`](SECURITY.md).

---

## 저장소 구조

```
hogumeter/
├── core/            # Spring Boot — 기준가 엔진·매칭·알림·REST (헥사고날)
├── collector/       # Python — 핫딜 3사 + 번개장터 폴링·파싱
├── web/             # React — 등록·판단(신호등·기준가·갭·주기·구매기록)·미상 큐·딜 보관함·중고·우선순위·설정
├── docs/            # 기획·설계 정본 (1·2차) — 읽기 순서는 docs/README.md
├── working-area/    # 작업 보드 (진행 로그·결정·배포 체크리스트)
├── scripts/         # 종단 스모크 · 백업/복원/롤백/오프사이트 드릴 (전부 CI가 돌린다)
├── .github/         # CI 워크플로 + Dependabot
├── .githooks/       # 선택 활성화: 커밋 전 시크릿 스캔 (+ 계약 테스트)
├── .claude/         # 에이전트 하네스 — rules(경로별 규칙) · hooks(브리핑·네트워크 가드)
├── docker-compose.yml
├── .env.example
└── CLAUDE.md        # 프로젝트 헌법 (작업 규율·TDD·기록 프로토콜)
```

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/README.md`](docs/README.md) | **문서 인덱스·읽기 순서** (여기부터) |
| [`docs/90-planning-final.md`](docs/90-planning-final.md) | 기획 확정본 v1.3 — **최종 권위** |
| [`docs/00-overview.md`](docs/00-overview.md) | 용어집·핵심 수치 |
| [`docs/03-deal-sets-and-time.md`](docs/03-deal-sets-and-time.md) | (2차 기반) 딜 집합·시간 좌표계 |
| [`CLAUDE.md`](CLAUDE.md) | 프로젝트 헌법(개발 규율·TDD·기록 강제) |

---

## 상태·한계 (정직하게)

- **1인용**(운영자 본인). 단, 서비스화 가능성을 고려해 코어(수집+기준가 엔진)와 전달(알림/웹)을 분리, 공식 API 기반 부품을 우선.
- **커뮤니티 크롤링은 약관 회색지대**다. 저빈도(게시판당 1req/min)·개인용으로 수용하며, 기술적 차단이 있는 곳은 우회하지 않는다(원칙5). 서비스화 시 데이터 계약 재검토가 필수 게이트.
- 자동으로 못 잡는 것(장바구니 쿠폰, 댓글 정정 등)은 경계를 긋고 "마지막 한 클릭은 사람 + 원문 링크"로 넘긴다.
- 코드는 **M0~M2·M5·M6 완료, M3·M4 대부분 완료**(위 로드맵 표 참조). 남은 것은 전부 코드 밖이다 — 텔레그램 봇 토큰 발급, 네이버 API 대체 데이터 소스 결정([D-7](working-area/decisions-needed.md)), 실 폴링 승인, 크롬 확장용 실 DOM fixture. 토큰·승인이 갖춰지면 즉시 동작한다.

## 라이선스

라이선스 명시 없음 — **개인 보유(All rights reserved).** 1인용·비공개 성격. 공개/서비스화 시 재결정.
