# collector — 핫딜/중고 수집기 (Python)

핫딜 3사(뽐뿌·루리웹·펨코)와 번개장터를 폴링·파싱해 PostgreSQL에 적재하는 별도 컨테이너. krepe90/user-hotdeal-bot 골격을 최대 재활용한다.

- 스택: **Python 3.12 · uv · pytest · BeautifulSoup**
- core와는 **DB 테이블 계약으로만** 접합(직접 호출 없음). collector는 무상태(마지막 폴링 커서 제외).

## 테스트

```bash
uv run pytest       # 파서 golden + 파이프라인
```

- 파서 테스트는 **fixture HTML/JSON만** 사용(실 네트워크 호출 금지). fixture는 `tests/fixtures/{site}/`.

## 구조

```
src/collector/
├── parsers/         # 사이트별 파서. 순수 함수: html/json → DTO (네트워크 분리)
│   ├── ppomppu.py / ruliweb.py / fmkorea.py / bunjang.py
│   └── models.py    # DTO
├── pipeline/        # 정규화(가격 추출)·적재 준비
│   ├── price.py     # 실결제가+배송비 규칙 기반 파싱 (as-posted, 카드 역산 금지)
│   └── ingest.py    # ParsedDeal → raw_deal_post 레코드 + 배치 멱등(자연키 dedup)
├── scheduler/       # 폴링 (1req/min 하한, 백오프, robots 존중, 차단 시 중지)
│   ├── policy.py    # 순수: 레이트 하한·결과 3분해·지수 백오프·사이트 상태 전이
│   ├── loop.py      # 사이트별 격리 1회 통과. fetch는 주입 포트
│   ├── sites.py     # 핫딜 3사 레지스트리 (URL·인코딩 — docs/98 실측)
│   └── fetcher.py   # Opener 포트 뒤 네트워크 격리 + robots 게이트
├── db/
│   └── raw_deal_sink.py   # raw_deal_post 업서트 (IO 전용, psycopg)
└── __main__.py      # 조립. COLLECTOR_ALLOW_NETWORK=1 없이는 요청하지 않는다
tests/
├── fixtures/{ppomppu,ruliweb,fmkorea,bunjang}/   # golden HTML/JSON
├── conftest.py      # Postgres 컨테이너 + core의 V1__init.sql 적용
├── test_parsers.py / test_price.py / test_ingest.py / test_scheduler.py
└── test_fetcher.py / test_pipeline_smoke.py / test_raw_deal_sink.py / test_main.py
```

## 테스트

```bash
uv run pytest                      # 전체 (통합 포함 — Docker 필요)
uv run pytest -m "not integration" # 빠른 루프, 컨테이너 없이 (~1초)
```

## 실행

```bash
uv run python -m collector          # 안내만 출력하고 종료 (요청 0회)
COLLECTOR_ALLOW_NETWORK=1 uv run python -m collector   # 실 폴링 — 사용자 승인 사항
```

두 개의 독립 스위치가 있다. **네트워크**는 `COLLECTOR_ALLOW_NETWORK=1`, **DB 적재**는 `DB_HOST` 설정. DB가 없으면 수집 결과를 화면에만 출력하고 그 사실을 알린다.

실 네트워크는 `fetcher.urllib_opener` **한 곳**뿐이고 테스트는 전부 fake opener를 쓴다. opt-in은 정지조건("실사이트 크롤링")을 산문이 아니라 기계로 강제하는 장치다.

## 미구현 (정직하게)
- **폴링 커서 영속화**(REL-03): 사이트별 상태(연속 실패·중지)가 메모리에만 있다. 재시작하면 초기화된다. 영속화하려면 **차단당한 사이트의 재개 경로**(`decisions-needed` D-3)가 먼저 정해져야 한다 — 안 그러면 영구 중지가 디스크에 남는다.
- **robots 실 대조**: `RobotsGate`는 구현됐으나(Q-38 해소) fake opener 테스트만 통과했다. 실 3사 robots.txt는 아직 안 봤다.
- **core 재처리**: 업서트로 상태변화가 `raw_deal_post`엔 남지만, 이미 `deal_event_source` 링크가 있으면 core가 재처리하지 않아 `deal_event`엔 반영되지 않는다 — `docs/91` Q-27.
- **파싱 드리프트 감지**(REL-06): 파싱 실패를 일시 장애로 흡수만 한다 — Q-40.
- **품절 감지 실검증**: 4사 모두 `list_normal` golden만 있어 SOLD_OUT 경로는 fixture로 확인되지 않았다 — Q-19.
- **번개 status 코드표**: `"0"=판매중`만 실측. 비-`"0"`을 전부 SOLD_OUT으로 보는 건 잠정 — Q-44.

## 사이트별 함정
- **뽐뿌**: `charset=euc-kr`을 선언하지만 실제로는 **cp949**로 디코딩해야 한다(`decode("euc-kr")`는 터진다). 목록에 뽐뿌마켓·자유게시판 위젯 행이 섞이므로 게시판 id로 필터. 오픈소스의 `#revolution_main_table` 셀렉터는 우리가 받는 응답에 없다 — 자세한 실측은 [`docs/98`](../docs/98-field-notes.md).

## 원칙
- **파서는 네트워크와 완전 분리**: fetch는 scheduler, 파싱은 parsers. 파서 테스트는 fixture만.
- **멱등**: `raw_deal_post`는 `(site, post_id)` UNIQUE로 **업서트**. 같은 글 두 번 넣어도 행 수 불변(REL-01)이고, 품절·가격변경은 기존 행에 반영된다(BM-01 AC-2). `posted_at`만 불변(발생 시각, C-2).
- **플랫폼 잣대**(원칙5): 공식 API 우선 / 기술적 차단 우회 금지 / 저빈도·개인용. 사이트 구조 변경은 파싱 성공률 하락으로 감지 → 관리 알림.
- 사이트별 실측(셀렉터·차단 징후·fixture 채취일)은 [`../docs/98-field-notes.md`](../docs/98-field-notes.md)에 기록.

## DB 계약 (core ↔ collector)
- `raw_deal_post`: collector가 업서트(`db/raw_deal_sink.py`). core가 소비 후 매칭·병합. **Flyway는 core 단독 소유** — collector는 마이그레이션하지 않고, 통합 테스트는 core의 `V1__init.sql`을 그대로 적용해 계약을 검증한다.
- `used_listing_observation`: 번개 폴링 관측 insert-only. core가 생애주기 판정.
- 스키마 진화는 **Flyway(core) 단독 소유** — collector는 마이그레이션 금지, 계약 테이블만 접근.
