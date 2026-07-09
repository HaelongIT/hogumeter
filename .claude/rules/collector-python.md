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
- 신규 런타임 의존은 **승인 대상**이다(현재 런타임 의존은 `beautifulsoup4` 하나). psycopg·Testcontainers-python 도입은 `docs/91` Q-36.
- **`print`로 나가는 문자열에 em dash(`—`)·이모지를 쓰지 않는다.** Windows 콘솔은 cp949라 `UnicodeEncodeError`로 죽는다. `capsys`는 utf-8로 캡처해 이를 못 잡으니, 출력 문구는 `text.encode("cp949")`로 단언한다. Alert reason도 결국 출력된다. (99: 2026-07-09)
- **엔트리포인트는 테스트 GREEN이어도 한 번은 실제로 실행해본다.**

## 파서

- **파서는 사이트 구조 변경에 터지지 않는다 — 조용히 0건을 낸다.** `soup.select()`는 못 찾으면 `[]`를 준다. 셀렉터 파싱의 실패 모드는 예외가 아니라 **침묵**이라 try/except가 못 잡는다. "성공했는데 산출물이 0"을 1급 경보 신호로 다룬다(`scheduler/drift.py`). (99: 2026-07-09)
- **"셀렉터가 없다"는 주장은 파서로 재현해 확인한다.** 상위 셀렉터 실패를 하위 셀렉터 부재로 일반화하지 않는다. `grep` 문자열 카운트는 bs4 결과를 대변하지 못한다(`class="x"` 8건 vs `select('.x')` 27건).

## 계약

- `raw_deal_post`는 `(site, post_id)` UNIQUE 멱등. `status` 허용집합은 **`ACTIVE`/`SOLD_OUT`/`DELETED`**(DB CHECK와 동일).
  ⚠️ `parse_bunjang`은 `ENDED`를 낸다 — 잠복 불일치, `docs/91` Q-41.
- 레이트 하한(게시판 60s / 마켓 600s)은 **설정으로 완화 불가**(SEC-08). `scheduler/policy.py`의 상수를 낮추지 말 것.
- 사이트 셀렉터·차단 징후·fixture 채취일의 실측은 `docs/98-field-notes.md`에 기록한다.
