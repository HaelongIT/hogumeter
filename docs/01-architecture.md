# 01. 아키텍처 & 모듈 경계 (TDD 이음새 정의)

## 컴포넌트 토폴로지
```
[collector(Python)] --write--> [PostgreSQL] <--read/write-- [core(Spring Boot)]
                                                   |-- REST --> [web(React)]
                                                   |-- REST <-- [extension(Chrome, 기능4 단계)]
                                                   |-- Bot API --> [Telegram]
```
- collector와 core의 접합은 **DB 테이블 계약**(raw_deal_post 등)으로만. 직접 호출 없음.
- 모든 컴포넌트 Docker Compose. AWS EC2 1대. 관리형 서비스 종속 금지(RDS 대신 Postgres 컨테이너 + 볼륨 + 백업).

## core (Spring Boot) — 헥사고날 지향 패키지 구조
TDD의 성패는 도메인의 순수성에 달려 있다. 아래 경계를 지킬 것.

```
core/
├── domain/                    # 순수 Java. Spring/JPA/IO 의존 금지. 단위 테스트만으로 완결
│   ├── product/               # Product, Variant, 축 모델
│   ├── deal/                  # DealEvent, 병합 판정, 상태기계(전이 규칙)
│   ├── benchmark/             # 기준가 엔진: 정규화, 이상치 판정, median/P25, 3단 표본 판정
│   ├── matching/              # 문자열 정규화·토큰화·별칭사전 매칭, 3단 판정(확정/후보/기각)
│   ├── alert/                 # 알림 판정: 트리거 평가, 최고강도 1발, 방해금지 보류, 후속 알림 규칙
│   ├── used/                  # 3계층 필터(AND/OR그룹 A·B모드/NOT), 매물 생애주기, 스냅샷
│   ├── purchase/              # (2차) Purchase 상태기계, 상태×트리거 판정, 스냅샷·성적표 as-of. docs/15
│   ├── digest/                # (2차) 다이제스트 창·귀속·섹션 합성(순수). docs/18
│   ├── watch/                 # (2차·[WATCH-유보]) 핀 자격·결말 전이·회고. docs/17
│   └── priority/              # (2차·[PRI-계류]) 대기 술어·발화 판정. docs/19
│   #  신호·주기(SIG/CAD, docs/16)는 신규 수집 0 read-model — benchmark 위 순수 함수(색 판정·주기)로 배치, 모듈 승격은 구현 시 판단
│   #  집합 술어·시간 좌표계(docs/03)는 deal/benchmark의 공유 순수 규칙
├── application/               # 유스케이스 오케스트레이션. port 인터페이스 정의
│   └── port/ (out: repository, naverClient, telegramSender, clock / in: usecase)
└── adapter/
    ├── persistence/           # JPA 구현. @DataJpaTest + Testcontainers
    ├── web/                   # REST 컨트롤러 (web + extension ingest). @WebMvcTest
    ├── telegram/              # 봇 어댑터 (발송 + 인라인 버튼 콜백 → reviewQueue 유스케이스)
    ├── naver/                 # 네이버 쇼핑 API 클라이언트. WireMock 테스트
    └── scheduler/             # 알림 평가·방해금지 플러시·캐시 만료
```

**규칙**
- domain은 어떤 프레임워크 애노테이션도 갖지 않는다. 시간은 `Clock` 주입, 난수 금지.
- 모든 판정 로직(기준가·이상치·매칭·알림·필터)은 "입력 → 판정 결과" 순수 함수 시그니처로 설계한다. 예: `AlertDecision decide(DealEvent deal, AlertPolicy policy, BenchmarkView bm)`.
- adapter는 얇게. 로직이 adapter에 스며들면 리팩토링으로 domain에 밀어 넣는다.

## collector (Python) — 파이프라인 구조
```
collector/
├── parsers/          # 사이트별 목록/글 파서. 순수 함수: html(str) -> list[DealPostDTO]
│   ├── ppomppu.py / ruliweb.py / fmkorea.py / bunjang.py
├── pipeline/         # dedupe(글 ID), 정규화(가격 추출), DB 적재
├── scheduler/        # 폴링 루프 (게시판당 1req/min, 백오프)
└── tests/fixtures/   # 사이트별 golden HTML (스파이크에서 채취, 갱신 규칙은 21 문서)
```
- 파서는 네트워크와 완전 분리: fetch는 scheduler, 파싱은 parsers. 파서 테스트는 fixture만 사용.
- 사이트 구조 변경 감지: 파싱 성공률이 임계 이하로 떨어지면 텔레그램 관리 알림 (20 문서).

## DB 계약 (collector ↔ core)
- `raw_deal_post`: collector가 `(site, post_id)` 자연키로 **업서트**. 재수집에 행 수 불변(REL-01 멱등), **상태·가격·추천수 변화는 기존 행에 반영**(BM-01 AC-2 — insert-only면 품절을 영원히 모른다). `posted_at`은 글의 발생 시각이라 **불변**이되 처음에 못 얻었으면 나중에 채운다(v1.3 C-2). core가 소비 후 매칭·병합. **[2026-07-09 정정]** 이전 서술("insert-only")은 core의 `RawDealPostUpserter`·`RawDealPostUpsertTest`가 단언하는 갱신 의미와 모순이었다.
- `used_listing_observation`: 번개 폴링 관측 insert-only. core가 생애주기 판정. **⚠️ 아직 존재하지 않는다** — V1·V2 어디에도 이 테이블은 없다(M2 중고 착수 시 core가 만든다). 계약이 아니라 **계획**이다. 그래서 `parse_bunjang`은 어디에도 배선돼 있지 않다.
- 스키마 진화는 Flyway 단독 소유(core). collector는 마이그레이션 금지, 계약 테이블만 접근. **통합 테스트는 `db/migration/V*__*.sql`을 버전 순서대로 전부 적용**한다 — 일부만 적용하면 그것도 미러다.

## 상태 소유권
- 기준가·알림·큐·설정 = core 소유. collector는 무상태(마지막 폴링 커서 제외).
- 멱등성: 같은 글/관측을 두 번 넣어도 결과 불변이어야 한다. 모든 ingest 경로에 자연키 UNIQUE.
