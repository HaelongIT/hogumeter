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
├── conftest.py      # Postgres 컨테이너 + core의 V*__*.sql 전부 적용(버전순)
├── test_parsers.py / test_price.py / test_ingest.py / test_scheduler.py
├── test_end_to_end_ingest.py   # golden → 파싱 → 정규화 → 실 Postgres 관통 (제약 통과 증명)
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

두 개의 독립 스위치가 있다. **네트워크**는 `COLLECTOR_ALLOW_NETWORK=1`, **DB 적재**는 `DB_HOST` 설정. DB가 없으면 수집 결과를 로그로만 남기고 그 사실을 알린다.

## 수명 계약 (compose `restart: on-failure`가 여기 의존한다)

| 상황 | 동작 | exit |
|---|---|---|
| opt-in 없음 | `refused` 이벤트 1줄 후 종료 — 재시작하지 않는다(정상 종료) | 0 |
| opt-in 있음 | 상주 루프(15초 틱). 폴링 주기는 사이트별 하한(60s)이 강제 | — |
| `SIGTERM`(`docker stop`) | **현재 사이클을 마치고** `stopped` 이벤트 후 종료 | 0 |
| 적재 연속 실패 3회 | `giving_up` 이벤트 후 종료 → 재시작·사람에게 넘긴다 | 1 |

틱 대기는 `time.sleep`이 아니라 `Event.wait`다. `time.sleep`은 신호 처리 후 **남은 시간을 마저 자서**(PEP 475) docker의 유예(기본 10초)를 넘긴다. `stop_grace_period: 30s`.

DB 일시장애(연결 끊김·재시작)는 수집 루프를 죽이지 않는다. `sink_error` 이벤트로 **몇 건을 잃었는지** 남기고 다음 틱에 계속한다 — 그 딜들은 대개 다음 폴링에서 목록에 그대로 있어 다시 잡힌다(Q-54).

## 로그 (OBS-01)

stdout은 **JSON Lines**다. `docker logs`가 유일한 관측 창구이므로 문장이 아니라 이벤트를 낸다.

```json
{"ts":"...","event":"cycle","sites_polled":3,"deals":69,"by_site":{"ppomppu":21,...},
 "failures":0,"blocked":0,"alerts":0,"stopped_sites":[],"written":69}
{"ts":"...","event":"alert","kind":"drift","site":"ppomppu","reason":"..."}
```

한글은 `\uXXXX`로 이스케이프된다 — 값은 온전하되 출력은 순수 ASCII라 **어떤 콘솔 인코딩에서도 죽지 않는다**. `by_site`의 0은 지우지 않는다: "성공했는데 0건"이 구조 변경의 전형이다.

실 네트워크는 `fetcher.urllib_opener` **한 곳**뿐이고 테스트는 전부 fake opener를 쓴다. opt-in은 정지조건("실사이트 크롤링")을 산문이 아니라 기계로 강제하는 장치다.

## 미구현 (정직하게)
- **폴링 커서 영속화**(REL-03): 사이트별 상태(연속 실패·중지)가 메모리에만 있다. 재시작하면 초기화된다. 영속화하려면 **차단당한 사이트의 재개 경로**(`decisions-needed` D-3)가 먼저 정해져야 한다 — 안 그러면 영구 중지가 디스크에 남는다.
- **robots 실 대조**: `RobotsGate`는 구현됐고(Q-38 해소) 실 소켓 경로도 리허설된다(`scripts/check-robots-drill.sh`, CI `robots` 잡). 그러나 **실 3사 robots.txt는 아직 안 봤다** — `ALLOW_REAL_ROBOTS=1 bash scripts/check-robots.sh`를 **사람이** 한 번 돌려야 하고(실 네트워크는 정지조건), 결과는 `docs/98`에 붙인다. `pre-deploy §F` 필수 항목.
- ~~**core 재처리**~~ **해소(2026-07-10)**: `PipelineScheduler`가 60초마다 ingest → 가격 재처리 → 종료 판정을 돈다. 업서트한 상태·가격 변화가 이제 `deal_event`까지 간다(`docs/91` Q-27 ①⑤). 잔여: 최초 수집 시 이미 품절인 원문·애매글 재스캔(Q-27 ③④).
- **품절 감지 실검증**: 4사 모두 `list_normal` golden만 있어 SOLD_OUT 경로는 fixture로 확인되지 않았다 — Q-19.
- **번개 status 코드표**: `"0"=판매중`만 실측. 비-`"0"`을 전부 SOLD_OUT으로 보는 건 잠정 — Q-44.
- **크기 상한**(SEC-05, 구현됨): title 300자 · url 2000자 · post_id 64자 · raw 256KiB. 넘으면 **자르지 않고 거절**하고 `oversized` 이벤트로 남긴다. 상한값은 golden 89건 실측 최대의 수 배이며 전수 대조로 오차단 0건 확인(Q-55 해소).
- **드리프트 임계**(REL-06 구현됨): `window=10 / 성공률 0.6 / 조용한 0건 3연속`은 실 수집 없이 정한 잠정값 — Q-45.
- **자정 경계 가정**: 목록 시각 파싱은 "게시판이 자정에 표기 형식을 바꾼다"는 가정에 기댄다. 실 수집 샘플로 확인 필요 — Q-23.

## 사이트별 함정
- **뽐뿌**: `charset=euc-kr`을 선언하지만 실제로는 **cp949**로 디코딩해야 한다(`decode("euc-kr")`는 터진다). 목록에 뽐뿌마켓·자유게시판 위젯 행이 섞이므로 게시판 id로 필터. 오픈소스의 `#revolution_main_table` 셀렉터는 우리가 받는 응답에 없다 — 자세한 실측은 [`docs/98`](../docs/98-field-notes.md).

## 원칙
- **파서는 네트워크와 완전 분리**: fetch는 scheduler, 파싱은 parsers. 파서 테스트는 fixture만.
- **멱등**: `raw_deal_post`는 `(site, post_id)` UNIQUE로 **업서트**. 같은 글 두 번 넣어도 행 수 불변(REL-01)이고, 품절·가격변경은 기존 행에 반영된다(BM-01 AC-2). `posted_at`만 불변(발생 시각, C-2).
- **플랫폼 잣대**(원칙5): 공식 API 우선 / 기술적 차단 우회 금지 / 저빈도·개인용. 사이트 구조 변경은 파싱 성공률 하락으로 감지 → 관리 알림.
- 사이트별 실측(셀렉터·차단 징후·fixture 채취일)은 [`../docs/98-field-notes.md`](../docs/98-field-notes.md)에 기록.

## DB 계약 (core ↔ collector)
- `raw_deal_post`: collector가 업서트(`db/raw_deal_sink.py`). core가 소비 후 매칭·병합. **Flyway는 core 단독 소유** — collector는 마이그레이션하지 않고, 통합 테스트는 core의 `db/migration/V*__*.sql`을 **버전 순서대로 전부** 적용해 계약을 검증한다(일부만 적용하면 그것도 미러다).
- `used_listing_observation`: **아직 없는 테이블이다.** V1·V2 어디에도 없다 — M2(중고) 착수 시 core가 만든다. 그래서 `parse_bunjang`은 fixture 테스트만 있고 스케줄러·적재기 어디에도 배선돼 있지 않다.
- 스키마 진화는 **Flyway(core) 단독 소유** — collector는 마이그레이션 금지, 계약 테이블만 접근.
