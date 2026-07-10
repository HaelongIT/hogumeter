---
paths:
  - "collector/**/*.py"
  - "collector/**/*.toml"
---

# collector (Python 3.12 · uv · pytest) 규율

> 전문은 `docs/99-lessons.md`·`docs/98-field-notes.md`. 여기엔 한 줄 규칙만 둔다.
> `collector/` 파일을 열 때만 로드된다 — core 작업 시엔 비용 0.

## 네트워크·순수성

- **파서는 네트워크와 완전 분리**한다. fetch는 `scheduler`, 파싱은 `parsers`. 파서 테스트는 `tests/fixtures/{site}/`의 golden 파일만 쓴다 — **실 사이트 호출 테스트 금지**(스파이크 제외).
- 실 사이트 크롤링·외부 API 실호출은 **정지조건**이며 `.claude/hooks/guard.sh`가 차단한다. 검증이 필요하면 fixture를 쓰거나 사용자 승인을 받는다.
- **순수 함수에 `now()`를 쓰지 않는다.** 시각은 주입(`captured_at`, `now`) — 그래야 테스트가 결정적이다(`pipeline/ingest.py`, `scheduler/policy.py`가 이 규약을 따른다).
- 테스트에 `sleep`·랜덤 금지(`docs/21`). 주기·백오프는 `now` 비교로 판정한다.

## 도구

- 테스트: `uv run pytest`. **shebang/경로 문제로 죽으면** `uv run python -m pytest`로 우회하거나 `uv sync --reinstall`로 venv 재생성(콘솔 스크립트가 옛 경로로 baked됨). (99: 2026-07-04)
- 신규 런타임 의존은 **승인 대상**이다. 현재 런타임 의존은 `beautifulsoup4`·`psycopg[binary]` 둘, 테스트 의존에 `testcontainers[postgres]`(2026-07-09 승인, decision-log).
- **stdout은 JSON Lines다**(OBS-01, `observability.py`). 문장이 아니라 이벤트를 낸다 — 테스트·스모크는 문자열을 grep하지 말고 `json.loads`로 이벤트를 읽는다(문구는 바뀌어도 계약은 안 바뀐다). `ensure_ascii`가 한글을 이스케이프해 cp949 콘솔에서도 죽지 않는다. 그래도 출력 문구는 `text.encode("ascii")`로 단언한다. (99: 2026-07-09)
- **엔트리포인트는 테스트 GREEN이어도 한 번은 실제로 실행해본다.**

## 프로세스 수명 (compose가 이 계약을 읽는다)

- **종료 코드로 말한다.** 정상 종료(opt-in off·SIGTERM) = `0`, 포기(적재 연속 실패) = `1`. `restart: on-failure`가 이 구분에 기댄다 — `always`로 바꾸면 opt-in 꺼진 컨테이너가 refused 메시지를 영원히 반복한다. **로그로 수명을 단언하지 않는다**: 스모크 6-1이 `exitCode:restartCount:policy == 0:0:on-failure`와 `refused` 정확히 1회를 직접 본다(99: 2026-07-10).
- **신호는 현재 사이클을 마치고 받는다.** 틱 대기는 `time.sleep`이 아니라 `Event.wait`다 — `time.sleep`은 신호 처리 후 **남은 시간을 마저 자서**(PEP 475) docker 유예를 넘긴다. 신호 등록 같은 부작용은 `__main__` 가장자리에만 두고 `main()`은 주입받는다.
- **적재 실패는 뭉개지도 죽지도 않는다.** `sink_error`로 유실 건수를 남기고 계속하되, 연속 `SINK_FAILURE_LIMIT`회면 스스로 내려온다. "수집은 되는데 저장이 안 되는 상태"로 도는 것이 최악이다. 실패 사이클의 `written`은 `0`이 아니라 **부재**여야 한다. (99: 2026-07-09)

## 파서

- **파서는 사이트 구조 변경에 터지지 않는다 — 조용히 0건을 낸다.** `soup.select()`는 못 찾으면 `[]`를 준다. 셀렉터 파싱의 실패 모드는 예외가 아니라 **침묵**이라 try/except가 못 잡는다. "성공했는데 산출물이 0"을 1급 경보 신호로 다룬다(`scheduler/drift.py`). (99: 2026-07-09)
- **"셀렉터가 없다"는 주장은 파서로 재현해 확인한다.** 상위 셀렉터 실패를 하위 셀렉터 부재로 일반화하지 않는다. `grep` 문자열 카운트는 bs4 결과를 대변하지 못한다(`class="x"` 8건 vs `select('.x')` 27건).

## 계약

- `raw_deal_post`는 `(site, post_id)` UNIQUE **업서트**. `status` 허용집합은 **`ACTIVE`/`SOLD_OUT`/`DELETED`**(DB CHECK와 동일). `ENDED`는 `deal_event.status`의 값이지 여기 값이 아니다 — 한때 `parse_bunjang`이 이걸 내 `to_raw_records`가 터졌다(해소 2026-07-09).
  ⚠️ 번개 status 코드표는 여전히 **미실측**(`"0"=판매중`만 안다). 비-`"0"`을 전부 `SOLD_OUT`으로 보는 건 잠정 — `docs/91` Q-44.
- 레이트 하한(게시판 60s / 마켓 600s)은 **설정으로 완화 불가**(SEC-08). `scheduler/policy.py`의 상수를 낮추지 말 것.
- 사이트 셀렉터·차단 징후·fixture 채취일의 실측은 `docs/98-field-notes.md`에 기록한다.
- **SEC-07: `raw`(jsonb)에 담는 키는 허용집합 안에서만 늘린다.** 번개 응답에는 `uid`(판매자 식별자)·`location`(동 단위 주소)·`imp_id`(광고 추적자)가 온다 — `raw={**item}` 한 줄이면 전부 DB로 간다. `tests/test_privacy.py`가 golden 전수로 키와 **값**을 함께 잠근다(키 이름만 바꾼 우회도 잡는다). (99: 2026-07-10)
