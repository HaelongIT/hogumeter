# C2 — collector 수집 런타임 리뷰

## 요약 (High 1 / Medium 2 / Low 0 / Info 1)

핵심 3분해(`OK/TRANSIENT/BLOCKED`)·robots 게이트·사이트 격리·opt-in 게이트 자체는 견고하고 테스트로 촘촘히 관통돼 있다. 다만 **DB 쓰기 실패 시 트랜잭션 롤백이 어디에도 없어 커넥션이 영구 오염되고**(C2-01, High), **가격-드리프트 알림의 재알림 억제가 정확히 그 실패 모드(가격 전무)에서만 무력화**되며(C2-02, Medium — 실행해 재현 확인함), **robots.txt 5xx를 404와 동일하게 "전체 허용"으로 처리**한다(C2-03, Medium). 세 건 모두 코드 인용과(C2-02는 실행 재현까지) 근거를 확보했고, 기존 테스트가 정확히 이 경계를 비켜간다.

---

### C2-01 — DB 쓰기 실패 후 rollback 부재로 커넥션이 영구 오염, 연쇄적으로 모든 쓰기가 실패한다 · High

- **위치**: `collector/src/collector/db/raw_deal_sink.py:54-61`(`RawDealSink.upsert_all`), 동일 패턴이 `db/used_listing_sink.py:43-57`, `db/site_poll_state_sink.py:59-87`에도 있다. 트리거 지점은 `collector/src/collector/__main__.py:150-159`.
- **근거**:
  `raw_deal_sink.py:54-61`
  ```python
  def upsert_all(self, records: list[RawDealRecord]) -> int:
      if not records:
          return 0
      with self.connection.cursor() as cursor:
          cursor.executemany(_UPSERT, [_params(record) for record in records])
      self.connection.commit()
      return len(records)
  ```
  예외가 나면 `commit()`은 실행되지 않고, 그 어떤 경로에서도 `self.connection.rollback()`이 호출되지 않는다. `__main__.py:150-159`:
  ```python
  if records:
      try:
          written = sink.upsert_all(records)
          sink_failures = 0
      except Exception as failure:
          written = None
          sink_failures += 1
          _log("sink_error", now, error=f"{type(failure).__name__}: {failure}",
               dropped=len(records), consecutive=sink_failures)
  ```
  여기도 로그만 남기고 `rollback()`을 부르지 않는다. 게다가 `__main__.py:94-103`(`_connect_if_needed`)에서 게시판 sink·중고 sink·poll_sink·alias_source가 **한 커넥션을 공유**하도록 명시적으로 설계돼 있다("같은 DB, 커넥션 하나면 족하다"). `psycopg.connect(...)`는 기본이 autocommit=False(트랜잭션 모드)다(`raw_deal_sink.py:80-91`의 `connect_from_env`에 autocommit 지정 없음). 저장소 전체에서 `rollback`을 grep하면 **0건**이다.

  이 시나리오가 이론상 가정이 아니라는 근거: `collector/tests/test_raw_deal_sink.py:158-165`가 실제로 CHECK 위반이 `executemany` 도중 던져지는 걸 이미 증명한다(status가 허용집합 밖이면 `psycopg.errors.CheckViolation`). 과거 `parse_bunjang`이 `ENDED`를 냈던 사고(REL/collector-python.md 기록)와 같은 클래스의 회귀가 재발하면 그대로 이 경로를 탄다.

- **실패 시나리오**: 파서가 (과거 `ENDED` 사고처럼) DB CHECK 제약을 벗어나는 값을 한 번이라도 내면 `sink.upsert_all()`이 예외를 던진다. `rollback()`이 없으므로 커넥션은 Postgres의 "current transaction is aborted" 상태로 남는다. 같은 사이클의 `_record_polls`(같은 커넥션의 `poll_sink.persist_states`)도 즉시 실패해 `poll_state_error`가 뜬다. **다음 사이클**의 `sink.upsert_all` 호출은 사이트가 정상 응답하고 파싱도 성공해도 "aborted transaction" 오류로 즉시 실패한다 — `used_sink.insert_batch`(중고 폴링)도 같은 커넥션을 쓰므로 함께 실패한다. `SINK_FAILURE_LIMIT=3`(`__main__.py:65`)이므로 원인이 된 배드 레코드 단 1건이 몇 사이클 안에 `giving_up` → `exit(1)`로 프로세스 전체를 끌고 내려간다. `restart: on-failure`가 결국 재시작해 회복은 되지만, 코드 주석이 명시한 의도("DB 일시장애가 수집 루프를 죽이면 안 된다", REL-02 정신)와 반대로 **단발성 데이터 이슈가 board+market 양쪽의 모든 쓰기를 마비시킨 뒤 강제 종료**로 이어진다.
- **이미 막는 장치 확인**: `test_main.py`의 `BrokenSink`는 순수 파이썬 mock(`raise RuntimeError`)이라 psycopg 트랜잭션 오염을 재현하지 않는다 — `test_sink_failure_does_not_kill_the_collection_loop`·`test_repeated_sink_failure_stops_the_process_instead_of_spinning`은 "예외 후 재시도"만 검증하고 "예외 후 같은 커넥션의 다른 쓰기까지 실패하는가"는 다루지 않는다. `test_raw_deal_sink.py`는 테스트마다 `conftest.py:44-54`가 새 커넥션/스키마를 만들므로, CHECK 위반 이후 **같은 커넥션을 재사용**하는 시나리오 자체가 어떤 테스트에도 없다. `db/*.py` 어디에도 `rollback` 호출이 없다(grep 확인, 0건).
- **권고**: 각 sink의 쓰기 메서드에서 예외를 잡아 `self.connection.rollback()`을 호출한 뒤 재-raise하거나, `__main__.py`의 각 `except Exception` 블록에서 실패 즉시 커넥션을 rollback한다. 통합 테스트에 "쓰기 실패 → 같은 커넥션으로 다음 쓰기가 성공해야 한다"를 추가해 회귀를 잠근다.

---

### C2-02 — 가격-드리프트(priceless) 알림이 재알림 억제 없이 매 사이클 반복된다 · Medium

- **위치**: `collector/src/collector/scheduler/drift.py:80-81`(`_healthy`), `drift.py:56-77`(`observe`의 무장해제 로직).
- **근거**:
  ```python
  def _healthy(observation: SiteObservation) -> bool:
      return observation.outcome is Outcome.OK and observation.deal_count > 0
  ```
  `observe()`는 매 관측마다 `_healthy(observation)`이 참이면 `alerted = alerted - {site}`로 무장을 해제한다(68-69행). 그런데 `_healthy`는 **`priced_count`를 전혀 보지 않는다** — 딜은 계속 나오지만 가격이 전부 없는 상태(`deal_count>0, priced_count==0`)도 매번 "건강"으로 판정돼 무장이 즉시 풀리고, 바로 다음 줄에서 `_diagnose`가 다시 priceless 스트릭을 진단해 재알림을 낸다. zero-yield(`deal_count==0`)는 `_healthy`가 정확히 걸러내 무장 해제가 안 되므로 억제가 작동한다 — **priceless만 이 경로에서 빠진다.**

  **실행해서 재현 확인**(scratchpad에서 실 모듈로 실행, `DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=3)`):
  - priceless 연속 10사이클(`deal_count=28, priced_count=0`): 사이클별 알림 수 = `[0,0,1,1,1,1,1,1,1,1]` → **총 8회**(임계 도달 후 매 사이클).
  - 대조군 zero-yield 연속 10사이클(`deal_count=0`): 사이클별 알림 수 = `[0,0,1,0,0,0,0,0,0,0]` → **총 1회**(설계 의도대로 억제됨).
- **실패 시나리오**: 문서 자체가 명시하듯("오늘 찾은 파서 결함 다섯 중 셋이 이 부류였다", `drift.py:10`) 제목 셀렉터만 끊기는 사고가 실제로 가장 흔한 파서 결함이다. 이 상태가 되면 3사이클(임계) 뒤부터 게시판 주기(60초)마다 `"딜은 나오는데 N회 연속 가격이 하나도 없습니다"` 이벤트가 무한 반복돼 `docker logs`를 채운다. `observability.py`·`drift.py` 자체가 경계하는 바로 그 실패 양상("같은 증상으로 매 사이클 알림이 오면 아무도 안 본다", `test_drift.py:94-95` 주석 / "오차단은 사람이 게이트를 꺼 버리게 만든다", `drift.py:91`)이 코드로는 지켜지지 않는다.
- **이미 막는 장치 확인**: `test_drift.py:94-98`(`test_alert_is_not_repeated_every_cycle`)은 `_ok(0)`(zero-yield) 반복만 검증한다. `test_drift.py:151-160`(`test_deals_without_any_price_raise_drift`)는 3사이클만 돌리고 `assert alerts`(비어있지 않음)만 확인할 뿐 길이를 검증하지 않는다. `test_drift.py:173-184`(`test_price_yield_recovers_and_can_alert_again`)는 **명시적 회복(가격이 다시 채워짐)** 이후의 재알림만 다루고, 회복 없이 priceless가 지속되는 억제 시나리오는 어떤 테스트에도 없다.
- **권고**: `_healthy`를 진단 원인별로 분리한다 — 예를 들어 `observe`가 `_diagnose`의 활성 사유(zero-yield vs priceless)에 맞춰 "그 사유를 실제로 해소했는가"만 무장 해제 조건으로 쓰게 한다(priceless 스트릭이 활성인 동안은 `priced_count>0`인 관측만 회복으로 인정).

---

### C2-03 — robots.txt가 5xx(서버 오류)를 반환해도 404(부재)와 동일하게 "전체 허용"으로 처리한다 · Medium

- **위치**: `collector/src/collector/scheduler/fetcher.py:157-167`(`RobotsGate._load`).
- **근거**:
  ```python
  def _load(self, origin: str) -> _RobotsDoc | None:
      try:
          status, body = self.opener(f"{origin}/robots.txt")
      except Exception:
          return None  # 조회 실패 시 제약 없음(표준 관행). 사이트 자체 장애는 fetch에서 드러난다.
      if status != 200:
          return None  # 404 등 → robots 없음 = 전체 허용
      ...
  ```
  `urllib_opener`(`fetcher.py:36-52`)는 4xx·5xx에서 예외를 던지지 않고 `(status, body)`를 그대로 돌려준다(설계 의도, `test_fetcher.py:219-223`로 확인됨). 따라서 robots.txt가 **500/503을 반환해도 예외가 아니라 `status=500`으로 여기 도달**하고, `status != 200` 분기가 404와 5xx를 구분 없이 전부 "robots 없음 = 전체 허용"으로 접는다. RFC 9309 §2.3.1.3은 robots.txt가 "unreachable"(5xx류 서버 오류)일 때는 404(부재)와 달리 보수적으로 다루라고 권고하는데, 이 구현은 그 구분이 없다. 이 프로젝트가 stdlib `robotparser`의 와일드카드 미지원을 이유로 자체 매처를 새로 짠 배경(`fetcher.py:64-69`, 2026-07-22 실측: 루리웹 Disallow를 못 잡았던 사고)과 대비하면, 정작 "조회 실패를 어떻게 해석할지"의 표준 세부는 더 느슨한 채로 남아 있다.
- **실패 시나리오**: robots.txt 엔드포인트만 일시적으로 500/503을 내고(오리진 캐시 이슈 등) 목록 페이지(`/zboard/zboard.php?...`)는 정상 응답하는 상황이면, `RobotsGate.allows()`가 `True`를 반환해 그 사이클에 실제 페이지를 그대로 긁는다 — robots.txt 서버 오류를 "제약 정보를 못 얻었으니 이번엔 건너뛴다"가 아니라 "제약 없음"으로 해석하는 것은 이 프로젝트의 "robots.txt를 존중한다" 원칙(리뷰 지시 원칙 5)보다 느슨한 기본값이다.
- **이미 막는 장치 확인**: `test_fetcher.py:57-64`(`test_robots_absent_means_allowed`)는 404만, `test_fetcher.py:102-107`(`test_robots_fetch_failure_does_not_block_collection`)은 예외(ConnectionError)만 검증한다. robots.txt 자체가 500/503 **상태 코드**를 반환하는 케이스는 어디에도 테스트가 없다 — 500/503이 파라미터화된 테스트(`test_fetcher.py:150-154`)는 목적 페이지(`/hotdeal`) 응답에 대한 것이지 robots.txt 조회에 대한 것이 아니다.
- **권고**: `_load`에서 5xx는 4xx(404 등)와 분리해 "판정 불가 → 이번 사이클은 보수적으로 disallow(또는 TRANSIENT로 넘겨 건너뛰기)"로 처리한다.

---

### C2-04 — `site_poll_state_sink.py`가 `datetime`을 임포트하지 않는다 · Info

- **위치**: `collector/src/collector/db/site_poll_state_sink.py:59`(`def persist_states(self, states: Mapping[str, SiteState], now: datetime) -> int:`)
- **근거**: 파일 상단 임포트(`collections.abc.Mapping`, `dataclasses.dataclass`, `psycopg`)에 `datetime`이 없다. 다만 `from __future__ import annotations`(1행)로 어노테이션이 지연 평가(문자열)되므로, `typing.get_type_hints()`를 부르지 않는 한 런타임 오류는 나지 않는다(실측: 코드 경로상 아무도 그걸 부르지 않는다). 실패 시나리오를 구성할 수 없어 Info로 강등한다.
- **권고**: 사소하지만 `from datetime import datetime`을 추가해 정적 분석 도구·향후 리팩터를 위해 정직하게 해 둔다.

---

## 응답 분류 대조표 (policy.py)

| 입력(HTTP 상태·예외) | 분류 | 후속 동작 | 적절한가 |
|---|---|---|---|
| HTTP 200-299 | `OK` | 백오프 리셋, `next_attempt=now+interval` | 적절 |
| HTTP 403 | `BLOCKED` | `stopped=True`, 영구 중지 + Alert, 재시도 없음 | 적절(SEC-08 의도대로, `test_block_signal_reaches_classify_status`로 관통 검증됨) |
| HTTP 429 | `BLOCKED` | 동일 | 적절 |
| robots Disallow(→ fetcher가 403으로 매핑) | `BLOCKED` | 동일 | 적절 — 로직·의도 모두 문서화·테스트됨(`test_robots_disallow_maps_to_blocked_not_transient`) |
| HTTP 404(대상 페이지) | `TRANSIENT` | 지수 백오프(최대 30분) 후 재시도 | 명세대로지만 관찰: URL 오탈자·사이트 구조 영구 변경이면 무기한 재시도-백오프로만 흐른다(별도 감지 없음). 3분해 계약 위반은 아님(Low급 관찰) |
| HTTP 5xx(대상 페이지) | `TRANSIENT` | 백오프 후 재시도 | 적절 |
| **robots.txt 자체가 500/503** | 예외 아님 → `RobotsGate`가 `None`(robots 없음)으로 해석 → `allows()=True` | 실제 페이지를 그대로 요청 | **부적절 — C2-03** |
| 전송 예외(DNS 실패·타임아웃·`ConnectionError`) | `TRANSIENT`(`loop._poll`의 `except Exception`) | 백오프 후 재시도 | 적절, `test_urllib_opener_still_raises_on_transport_failures`로 확인 |
| 파싱 예외(구조 변경으로 파서가 throw) | `TRANSIENT`(동일 경로) | 백오프 후 재시도. drift는 success-rate 창(임계 미달)으로만 별도 포착 | 적절 — 문서가 명시한 트레이드오프("사이트 구조 변경이 일시 장애와 구분되지 않기 때문") |
| DB 쓰기 실패(sink 예외, `policy.py` 밖) | 별도 카운터 `sink_failures` | 3회 연속 시 `giving_up` → `exit(1)` | 메커니즘 자체는 적절하나 **rollback 부재로 조기 폭주 — C2-01** |

---

## 검토했으나 문제없음 (근거)

- **opt-in 게이트가 모든 네트워크 경로를 덮는다**: `__main__.main()`은 `ALLOW_NETWORK_ENV != "1"`이면 `RobotsGate`·`HttpFetcher` 생성 이전에 `return 0`한다(`__main__.py:87-91`) — opener를 참조하는 어떤 객체도 만들어지지 않는다.
- **사이트 격리**: `loop._poll`이 `fetch`·`parse`의 모든 예외를 `except Exception`으로 흡수해 `TRANSIENT`로 돌리므로 한 사이트의 실패가 `run_cycle`의 `for spec in specs` 루프를 끊지 않는다(`loop.py:100-131`).
- **SIGTERM 처리**: `SignalStopper.sleep`이 `time.sleep`이 아니라 `Event.wait`를 쓴다(`__main__.py:307-308`) — PEP 475로 인한 유예시간 초과 문제를 피한다. `should_stop()` 체크는 사이클이 끝난 뒤에만 이뤄진다(`__main__.py:192-195`) — 적재 중간에 프로세스가 찢기지 않는다.
- **UA·robots 준수**: `USER_AGENT = "hogumeter/0.1 (personal use)"`로 위장 없음(`test_user_agent_is_not_disguised`). `_compile_pattern`이 stdlib이 못 잡는 `Disallow: /*view=`류 와일드카드를 정확히 처리한다(2026-07-22 실측 기반 회귀 테스트 다수 확인).
- **SQL 파라미터 바인딩**: `raw_deal_sink.py`·`used_listing_sink.py`·`site_poll_state_sink.py`·`used_search_source.py`·`alias_source.py` 전부 `%(name)s` 스타일 바인딩만 쓰고 문자열 조립이 없다(SQL 인젝션 경로 없음).
- **`used_listing_sink`의 insert-only가 의도적**: "그 시각 목록의 스냅샷"이라 중복 삽입이 정상이며, core의 fold 로직이 dedupe를 담당한다고 명시돼 있고(`used_listing_sink.py:1-9`), 가격 없는 매물은 세어서(`skipped_no_price`) 버린다(조용히 버리지 않음).
- **워터마크 갱신 순서**: `_record_polls`는 `sink.upsert_all` **이후**에 호출된다(`__main__.py:150-166`) — 적재 성공 전에 커서를 먼저 진전시키지 않는다. (다만 C2-01의 커넥션 오염이 발생하면 `_record_polls` 자체도 같은 사이클에서 즉시 실패해 DB에 반영되지 않으므로, "쓰기 실패인데 워터마크만 전진"하는 별도의 거짓-신선도 경로는 확인되지 않았다 — C2-01에 흡수됨.)
- **market/board 커서 네임스페이스 분리**: `market_spec`의 `name=f"{platform.lower()}#{id}"`가 게시판 이름과 절대 충돌하지 않고, `run_cycle`은 `spec.name`에 없는 키를 그대로 통과시키므로 서로의 커서를 밟지 않는다(코드 추적으로 확인).
- **`site_poll_state_sink.py`의 `datetime` 미임포트**: `from __future__ import annotations`로 인해 런타임에 영향 없음(C2-04로 Info 강등해 별도 기록).

## 시간·범위 한계로 못 본 것

- **C2-01을 실 Postgres(Testcontainers)로 직접 재현하지는 않았다** — psycopg3 트랜잭션 의미론(autocommit 기본값 False, rollback 없이는 aborted 상태 지속)과 기존 `test_raw_deal_sink.py`의 CheckViolation 테스트를 근거로 코드 추론했다. 실 DB로 "쓰기 실패 → 같은 커넥션 재사용 → 후속 쓰기 실패" 왕복 재현은 하지 않았다.
- `pipeline/ingest.py`(`oversized`, `to_raw_records`)·`pipeline/detail_fetch.py`·`pipeline/price.py`·`parsers/*`는 이번 리뷰 범위(C2) 밖이라 표면적으로만(배선 확인 목적) 읽었고 깊이 검토하지 않았다.
- core 마이그레이션의 실제 컬럼 타입·길이 제약을 전수 대조해 C2-01을 촉발할 수 있는 **모든** 값 클래스(문자열 길이 초과, numeric overflow 등)를 확인하지는 않았다 — 과거 `ENDED` 사고와 같은 클래스의 CHECK 위반 재발 가능성으로만 논증했다.
- 텔레그램 부재로 인한 `Alert`(BLOCKED/drift)의 최종 소비처 부재는 `docs/91 Q-20`으로 이미 알려진 한계라 별도 재보고하지 않았다.
