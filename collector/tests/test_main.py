"""엔트리포인트 — 실 네트워크는 환경변수 opt-in 없이는 절대 나가지 않는다.

정지조건("실사이트 크롤링")을 산문이 아니라 기계로 강제한다. 이 테스트가 그 기계를 지킨다.
"""

import json
from datetime import datetime, timedelta, timezone

from collector.__main__ import ALLOW_NETWORK_ENV, SINK_FAILURE_LIMIT, _interval_port, main
from collector.scheduler.fetcher import RobotsGate
from collector.scheduler.loop import SiteSpec
from collector.scheduler.policy import SiteKind
from collector.scheduler.sites import hotdeal_boards


def _events(out: str) -> list[dict]:
    """stdout은 JSON Lines다(OBS-01). 문자열을 grep하지 말고 이벤트를 읽는다."""
    return [json.loads(line) for line in out.strip().splitlines() if line]

NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)


class RecordingOpener:
    def __init__(self):
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (404, b"")
        return (200, b"<html></html>")


def test_refuses_to_touch_network_without_optin(monkeypatch, capsys):
    monkeypatch.delenv(ALLOW_NETWORK_ENV, raising=False)
    opener = RecordingOpener()

    exit_code = main(opener=opener, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    assert exit_code == 0
    assert opener.calls == []  # 단 한 번도 나가지 않았다
    (refused,) = _events(capsys.readouterr().out)
    assert refused["event"] == "refused"
    assert refused["env"] == ALLOW_NETWORK_ENV  # 켜는 방법을 알려준다


def test_wrong_optin_value_is_still_a_refusal(monkeypatch):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "true")  # "1"만 허용
    opener = RecordingOpener()

    main(opener=opener, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    assert opener.calls == []


def test_optin_polls_every_registered_board_once(monkeypatch, capsys):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    opener = RecordingOpener()

    main(opener=opener, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    page_calls = [u for u in opener.calls if not u.endswith("/robots.txt")]
    assert sorted(page_calls) == sorted(s.url for s in hotdeal_boards())


def test_optin_does_not_sleep_after_the_last_cycle(monkeypatch):
    """max_cycles를 다 돌면 즉시 반환한다 — 테스트가 sleep에 매달리지 않게."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    slept: list[float] = []

    main(opener=RecordingOpener(), sleep=slept.append, clock=lambda: NOW, max_cycles=1)

    assert slept == []


class BlockingOpener(RecordingOpener):
    """robots가 전부 Disallow → Alert 경로를 태운다."""

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (200, b"User-agent: *\nDisallow: /\n")
        raise AssertionError("Disallow인데 페이지를 요청했다")


def _assert_console_safe(text: str) -> None:
    """Windows 콘솔은 cp949다. capsys는 utf-8로 캡처하므로 문자열 단언만으론 이 사고를 못 잡는다 —
    실제로 `—`가 엔트리포인트를 죽였다(docs/99). 이제 로그가 JSON(ensure_ascii)이라 구조적으로 안전하다."""
    text.encode("cp949")  # 실패하면 UnicodeEncodeError
    text.encode("ascii")  # 구조화 로그는 순수 ASCII여야 한다


def test_refusal_message_is_console_encodable(monkeypatch, capsys):
    monkeypatch.delenv(ALLOW_NETWORK_ENV, raising=False)

    main(opener=RecordingOpener(), sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    _assert_console_safe(capsys.readouterr().out)


# ── DB 적재 배선 (실 DB 없이 검증) ──────────────────────────────────────


class FakeSink:
    def __init__(self):
        self.batches: list[list] = []

    def upsert_all(self, records):
        self.batches.append(records)
        return len(records)


class OneDealOpener(RecordingOpener):
    """뽐뿌 페이지만 딜 1건짜리 최소 HTML로 응답한다."""

    _ROW = (
        '<table><tr class="baseList bbs_new1">'
        '<td class="baseList-numb">1</td>'
        '<td><a class="baseList-title" href="view.php?id=ppomppu&no=1">벨트 (1,000원/무료)</a></td>'
        '<td class="baseList-rec">3 - 0</td>'
        "</tr></table>"
    )

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (404, b"")
        if "ppomppu" in url:
            return (200, self._ROW.encode("cp949"))
        return (200, b"<html></html>")


def test_deals_are_upserted_when_a_sink_is_configured(monkeypatch):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    sink = FakeSink()

    main(
        opener=OneDealOpener(), sink=sink, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1
    )

    assert len(sink.batches) == 1
    (record,) = sink.batches[0]
    assert (record.site, record.post_id) == ("ppomppu", "1")
    assert record.captured_at == NOW  # 폴링 시각이 그대로 captured_at
    assert record.headline_price == 1_000


def test_no_sink_means_no_upsert_and_the_limitation_is_stated(monkeypatch, capsys):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    monkeypatch.delenv("DB_HOST", raising=False)

    main(opener=OneDealOpener(), sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    out = capsys.readouterr().out
    events = _events(out)
    started = next(e for e in events if e["event"] == "started")
    assert "sink" not in started  # DB 없으면 sink 필드 자체가 없다
    assert "DB 미설정" in started["message"]  # 숨기지 않는다
    assert all("written" not in e for e in events)
    _assert_console_safe(out)


def test_empty_cycle_does_not_touch_the_sink(monkeypatch):
    """딜이 0건이면 빈 배치를 DB에 보내지 않는다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    sink = FakeSink()

    main(opener=RecordingOpener(), sink=sink, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    assert sink.batches == []


def test_silent_zero_yield_raises_a_console_safe_drift_alert(monkeypatch, capsys):
    """REL-06: 파서가 예외 없이 계속 0건이면 구조 변경을 의심해 알린다.

    RecordingOpener는 3사 모두 빈 HTML을 준다 → 파싱 성공, 딜 0건. 3사이클이면 임계(3) 도달.
    시계를 전진시켜야 한다 — 고정 시계면 레이트 하한(60s) 때문에 2·3번째 사이클이 건너뛴다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(seconds=61 * i) for i in range(10))

    main(opener=RecordingOpener(), sleep=lambda _: None, clock=lambda: next(ticks), max_cycles=3)

    out = capsys.readouterr().out
    drift = [e for e in _events(out) if e["event"] == "alert" and e["kind"] == "drift"]
    assert len(drift) == 3  # 사이트별 1회씩, 반복되지 않는다
    assert all("3회 연속 0건" in e["reason"] for e in drift)
    assert sorted(e["site"] for e in drift) == sorted(s.name for s in hotdeal_boards())
    _assert_console_safe(out)


def test_alert_and_summary_output_are_console_encodable(monkeypatch, capsys):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")

    main(opener=BlockingOpener(), sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    out = capsys.readouterr().out
    events = _events(out)
    blocked = [e for e in events if e["event"] == "alert" and e["kind"] == "blocked"]
    assert len(blocked) == 3  # 3사 모두 robots Disallow
    cycle = next(e for e in events if e["event"] == "cycle")
    assert cycle["blocked"] == 3 and sorted(cycle["stopped_sites"]) == sorted(s.name for s in hotdeal_boards())
    _assert_console_safe(out)


# ── 상주 프로세스의 종료·실패 계약 ─────────────────────────────────────
#
# opt-in을 켜면 main은 영원히 돈다(max_cycles=None). 그러면 두 가지 계약이 생긴다:
# ① `docker stop`(SIGTERM)에 어떻게 반응하는가 ② 적재가 실패하면 무엇이 죽는가.


def test_stop_signal_finishes_the_current_cycle_and_exits_cleanly(monkeypatch, capsys):
    """SIGTERM은 사이클 중간에 프로세스를 찢지 않는다 — 그 사이클을 마치고 나간다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    stop = StopAfter(cycles=2)

    exit_code = main(
        opener=RecordingOpener(), sleep=stop.sleep, clock=lambda: NOW, should_stop=stop, max_cycles=None
    )

    assert exit_code == 0
    events = _events(capsys.readouterr().out)
    assert len([e for e in events if e["event"] == "cycle"]) == 2  # 2번째를 끝까지 돌았다
    stopped = next(e for e in events if e["event"] == "stopped")
    assert stopped["cycles"] == 2


def test_stop_signal_interrupts_the_sleep_rather_than_waiting_it_out(monkeypatch):
    """틱 대기 중에 신호가 오면 즉시 깬다. 안 그러면 docker가 10초 뒤 SIGKILL한다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    stop = StopAfter(cycles=1)

    main(opener=RecordingOpener(), sleep=stop.sleep, clock=lambda: NOW, should_stop=stop, max_cycles=None)

    assert stop.slept == []  # 마지막 사이클 뒤엔 잠들지 않는다


class StopAfter:
    """N 사이클 뒤 SIGTERM이 온 것처럼 군다."""

    def __init__(self, cycles: int):
        self._limit = cycles
        self._cycles = 0
        self.slept: list[float] = []

    def __call__(self) -> bool:
        self._cycles += 1
        return self._cycles >= self._limit

    def sleep(self, seconds: float) -> None:
        self.slept.append(seconds)


class BrokenSink:
    """psycopg가 던지는 일시 장애를 흉내낸다(DB 재시작·연결 끊김)."""

    def __init__(self, fail_times: int = 99):
        self.calls = 0
        self._fail_times = fail_times

    def upsert_all(self, records):
        self.calls += 1
        if self.calls <= self._fail_times:
            raise RuntimeError("connection already closed")
        return len(records)


def test_sink_failure_does_not_kill_the_collection_loop(monkeypatch, capsys):
    """DB 일시장애가 수집을 멈추면 안 된다. 실패는 이벤트로 남기고 다음 틱에 계속한다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(seconds=61 * i) for i in range(10))
    sink = BrokenSink(fail_times=1)

    exit_code = main(
        opener=OneDealOpener(), sink=sink, sleep=lambda _: None, clock=lambda: next(ticks), max_cycles=2
    )

    assert exit_code == 0
    assert sink.calls == 2  # 첫 사이클 실패 후에도 두 번째를 시도했다
    events = _events(capsys.readouterr().out)
    errors = [e for e in events if e["event"] == "sink_error"]
    assert len(errors) == 1
    assert errors[0]["dropped"] == 1  # 몇 건을 잃었는지 숨기지 않는다
    assert "RuntimeError" in errors[0]["error"]
    # 실패한 사이클의 written은 0이 아니라 부재다 — "0건 적재"와 "적재 못 함"은 다르다.
    first_cycle = next(e for e in events if e["event"] == "cycle")
    assert "written" not in first_cycle


def test_repeated_sink_failure_stops_the_process_instead_of_spinning(monkeypatch, capsys):
    """계속 실패하면 조용히 도는 대신 죽는다 — restart 정책과 사람이 받는다(실패를 뭉개지 않는다)."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(seconds=61 * i) for i in range(20))

    exit_code = main(
        opener=OneDealOpener(), sink=BrokenSink(), sleep=lambda _: None,
        clock=lambda: next(ticks), max_cycles=None,
    )

    assert exit_code == 1
    events = _events(capsys.readouterr().out)
    giving_up = next(e for e in events if e["event"] == "giving_up")
    assert giving_up["consecutive_sink_failures"] == SINK_FAILURE_LIMIT
    _assert_console_safe(capsys.readouterr().out)


def test_a_success_resets_the_failure_streak(monkeypatch):
    """한 번 성공하면 카운터는 0으로. 간헐적 실패로 프로세스가 죽으면 안 된다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(seconds=61 * i) for i in range(20))
    sink = BrokenSink(fail_times=SINK_FAILURE_LIMIT - 1)

    exit_code = main(
        opener=OneDealOpener(), sink=sink, sleep=lambda _: None,
        clock=lambda: next(ticks), max_cycles=SINK_FAILURE_LIMIT + 1,
    )

    assert exit_code == 0  # 임계 직전까지 실패했지만 성공이 끼어들어 살아남았다


def test_zero_deals_with_a_sink_reports_written_zero_not_absent(monkeypatch, capsys):
    """카운터에서 0을 생략하지 않는다(OBS-02). "0건 적재"와 "적재 못 함"은 다른 사건이다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")

    main(opener=RecordingOpener(), sink=FakeSink(), sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    cycle = next(e for e in _events(capsys.readouterr().out) if e["event"] == "cycle")
    assert cycle["deals"] == 0
    assert cycle["written"] == 0  # 부재가 아니다


def test_write_failure_is_distinguishable_from_writing_zero(monkeypatch, capsys):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")

    main(opener=OneDealOpener(), sink=BrokenSink(fail_times=1), sleep=lambda _: None,
         clock=lambda: NOW, max_cycles=1)

    events = _events(capsys.readouterr().out)
    cycle = next(e for e in events if e["event"] == "cycle")
    assert "written" not in cycle  # 못 썼다 (0을 썼다가 아니다)
    assert any(e["event"] == "sink_error" for e in events)


LONG_TITLE = "가" * 400 + " (1,000원/무료)"  # 파서는 가격 표기까지 제목에 담는다


class OversizedDealOpener(RecordingOpener):
    """뽐뿌 페이지가 비정상적으로 긴 제목을 낸다(SEC-05: 크롤링 텍스트는 비신뢰 입력)."""

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (404, b"")
        if "ppomppu" in url:
            long_title = LONG_TITLE
            row = (
                '<table><tr class="baseList bbs_new1">'
                '<td class="baseList-numb">1</td>'
                f'<td><a class="baseList-title" href="view.php?id=ppomppu&no=1">{long_title}</a></td>'
                '<td class="baseList-rec">3 - 0</td>'
                "</tr></table>"
            )
            return (200, row.encode("cp949"))
        return (200, b"<html></html>")


def test_oversized_deals_are_skipped_and_the_loss_is_logged(monkeypatch, capsys):
    """조용히 버리지 않는다 — 무엇을 왜 버렸는지 이벤트로 남긴다(SEC-05)."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    sink = FakeSink()

    main(opener=OversizedDealOpener(), sink=sink, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    out = capsys.readouterr().out
    events = _events(out)
    (skipped,) = [e for e in events if e["event"] == "oversized"]
    assert skipped["field"] == "title"
    assert skipped["limit"] == 300 and skipped["size"] == len(LONG_TITLE)
    assert (skipped["site"], skipped["post_id"]) == ("ppomppu", "1")

    # 잘린 제목이 DB로 흘러들지 않는다 — 배치 자체가 비어 sink를 부르지 않는다.
    assert sink.batches == []
    cycle = next(e for e in events if e["event"] == "cycle")
    assert cycle["deals"] == 1 and cycle["skipped"] == 1 and cycle["written"] == 0
    _assert_console_safe(out)


def test_normal_cycle_reports_zero_skipped(monkeypatch, capsys):
    """카운터에서 0을 생략하지 않는다(OBS-02)."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")

    main(opener=OneDealOpener(), sink=FakeSink(), sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    cycle = next(e for e in _events(capsys.readouterr().out) if e["event"] == "cycle")
    assert cycle["skipped"] == 0 and cycle["written"] == 1


# ── SEC-08: robots Crawl-delay가 실제로 폴링 주기에 반영되는가 ─────────
#
# `effective_interval_with_robots`는 처음부터 GREEN이었다 — **부르는 곳이 없었을 뿐이다.**
# `run_cycle`은 우리 하한(60초)만 봤고, 뽐뿌가 120초를 선언해도 60초마다 두드렸다.
# 그래서 여기엔 두 층의 테스트가 있다:
#   ① `_interval_port` 합성 규칙 (max(설정, Crawl-delay, 하한))
#   ② **`main()`이 그 포트를 실제로 주입하는가** — ①만으로는 오늘의 버그를 한 층 위에 재현한다.


class CrawlDelayOpener(RecordingOpener):
    """robots.txt로 `Crawl-delay`를 선언하는 사이트. 페이지는 빈 HTML."""

    def __init__(self, seconds: int):
        super().__init__()
        self._robots = f"User-agent: *\nCrawl-delay: {seconds}\n".encode()

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (200, self._robots)
        return (200, b"<html></html>")


def test_declared_crawl_delay_actually_throttles_the_polling_loop(monkeypatch):
    """robots가 1시간을 요구하면 두 번째 사이클엔 폴링하지 않는다.

    이것이 배선을 보는 유일한 테스트다 — `main()`에서 `interval_for=`를 지우면 여기만 RED가 되고
    아래 단위 테스트 3개는 GREEN을 유지한다. 순수 함수의 GREEN은 호출자의 존재를 증명하지 않는다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    opener = CrawlDelayOpener(seconds=3600)
    # clock(): 루프 진입 전 1회 + 사이클당 1회. 두 번째 사이클은 90초 뒤 — 하한(60초)이라면 due다.
    ticks = iter([NOW, NOW, NOW + timedelta(seconds=90)])

    main(opener=opener, sleep=lambda _: None, clock=lambda: next(ticks), max_cycles=2)

    pages = [u for u in opener.calls if not u.endswith("/robots.txt")]
    assert len(pages) == len(hotdeal_boards())  # 사이클 2회, 사이트당 폴링은 1회뿐

    # robots.txt는 호스트당 1회만 — fetcher와 주기 포트가 같은 RobotsGate(캐시)를 공유한다.
    robots = [u for u in opener.calls if u.endswith("/robots.txt")]
    assert len(robots) == len(set(robots)) == len(hotdeal_boards())


def _board(seconds: int = 60) -> SiteSpec:
    return SiteSpec(name="ppomppu", kind=SiteKind.BOARD, interval=timedelta(seconds=seconds),
                    url="https://www.ppomppu.co.kr/zboard/list.php", encoding="utf-8",
                    parse=lambda body, now: [])


def _robots(body: str) -> RobotsGate:
    return RobotsGate(opener=lambda url: (200, body.encode("utf-8")))


def test_declared_crawl_delay_longer_than_ours_wins():
    """뽐뿌가 120초를 선언하면 우리는 120초를 기다린다(SEC-08, 절대 원칙 5)."""
    port = _interval_port(_robots("User-agent: *\nCrawl-delay: 120\n"))

    assert port(_board(60)) == timedelta(seconds=120)


def test_declared_crawl_delay_shorter_than_our_floor_loses():
    """`설정으로 완화 불가` — 사이트가 1초를 허락해도 게시판 하한 60초를 지킨다."""
    port = _interval_port(_robots("User-agent: *\nCrawl-delay: 1\n"))

    assert port(_board(60)) == timedelta(seconds=60)


def test_no_declared_delay_falls_back_to_our_floor():
    port = _interval_port(_robots("User-agent: *\nDisallow: /admin/\n"))

    assert port(_board(1)) == timedelta(seconds=60)  # 설정이 짧아도 하한으로 clamp


def test_robots_lookup_failure_does_not_break_polling():
    """robots를 못 읽으면 제약 없음(표준 관행). 그래도 우리 하한은 지킨다."""

    def broken(url: str):
        raise OSError("연결 거부")

    port = _interval_port(RobotsGate(opener=broken))

    assert port(_board(60)) == timedelta(seconds=60)


# 파서가 실제로 돌아 "딜은 나오는데 가격이 하나도 없다"를 만든다. fmkorea만 가격을 못 뽑고
# ppomppu·ruliweb은 정상(가격 있음) — REL-06 priceless 드리프트가 **해당 사이트만** 발화하는가.
class _PricelessFmkoreaOpener:
    """fmkorea 가격칸을 파싱 불가로, 나머지 두 사이트는 정상 딜로 준다."""

    def __init__(self) -> None:
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (404, b"")
        if "ppomppu" in url:  # cp949, 가격 있는 제목
            row = (
                '<table id="revolution_main_table"><tr class="baseList bbs_new1">'
                '<td class="baseList-numb">7</td>'
                '<td><a class="baseList-title" href="view.php?id=ppomppu&no=7">벨트 (11,800원/무료)</a></td>'
                '<td class="baseList-rec">3 - 0</td>'
                '<td class="baseList-time">21:10:11</td></tr></table>'
            )
            return (200, row.encode("cp949"))
        if "ruliweb" in url:  # 정상 딜
            row = (
                '<table class="board_list_table"><tr class="table_body normal">'
                '<td><input class="info_article_id" value="777"></td>'
                '<td><div class="title_wrapper subject relative">'
                '<a class="subject_link deco" href="https://bbs.ruliweb.com/market/board/1020/read/777">'
                '키보드 (49,000원/무료)</a></div></td>'
                '<td class="recomd"><strong>5</strong></td><td class="time">21:10</td></tr></table>'
            )
            return (200, row.encode("utf-8"))
        # fmkorea: `.hotdeal_info`의 가격칸이 파싱 불가 → 딜은 만들어지되 headline_price=None
        row = (
            '<div id="content"><div class="fm_best_widget"><ul><li>'
            '<h3 class="title"><a href="/999">가격문의 제품</a></h3>'
            '<div class="hotdeal_info"><span><a>쿠팡</a></span>'
            '<span><a>가격문의</a></span><span><a>무료</a></span></div>'
            '<span class="regdate">21:10</span></li></ul></div></div>'
        )
        return (200, row.encode("utf-8"))


def test_priceless_deals_raise_a_drift_alert_only_for_the_affected_site(monkeypatch, capsys):
    """REL-06: 제목 셀렉터만 끊기면 딜 수는 그대로인데 가격이 전부 사라진다.

    이 신호가 **엔트리포인트에서 실제로 발화**하는지 본다 — `run_cycle`이 채운 priced_count가
    `observe`까지 흘러야 한다(zero-yield 종단 테스트는 그 필드가 흐르는지 안 본다).
    fmkorea만 priceless라 fmkorea만 알림이 나야 한다 — 정상 사이트를 오탐하면 사람이 게이트를 끈다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(seconds=61 * i) for i in range(10))

    main(opener=_PricelessFmkoreaOpener(), sleep=lambda _: None, clock=lambda: next(ticks), max_cycles=3)

    out = capsys.readouterr().out
    drift = [e for e in _events(out) if e["event"] == "alert" and e["kind"] == "drift"]
    priceless = [e for e in drift if "가격이 하나도 없습니다" in e["reason"]]
    assert len(priceless) == 1  # fmkorea, 사이트별 1회(반복 안 함)
    assert priceless[0]["site"] == "fmkorea"
    # ppomppu·ruliweb은 정상(가격 있음)이라 어떤 드리프트도 나면 안 된다(오차단 방지).
    assert all(e["site"] == "fmkorea" for e in drift), [e["site"] for e in drift]
    _assert_console_safe(out)


class _AlwaysFailOpener:
    """모든 요청이 전송 실패(예외). robots 조회 실패는 제약 없음으로 흡수되고, 페이지 fetch는
    `_poll`이 TRANSIENT로 격리한다 → 매 폴링 성공률 0%."""

    def __init__(self) -> None:
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        raise OSError("연결 거부")


def test_sustained_transient_failures_raise_a_low_success_rate_drift(monkeypatch, capsys):
    """REL-06 세 번째 신호: 창이 꽉 찰 만큼 실패가 이어지면 '수집 불안정'을 알린다.

    zero-yield·priceless와 다른 경로다(그 둘은 OK인데 산출이 0). 이것은 TRANSIENT 비율.
    백오프(cap 30min)로 next_attempt_at이 밀리므로 시계를 1시간씩 전진시켜 매 사이클 due를 만든다.
    창 크기(10)만큼 사이클을 돌려야 성공률 판정이 돈다(첫 실패로 터지지 않게 — 오차단 방지).
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    ticks = iter(NOW + timedelta(hours=i) for i in range(14))

    main(opener=_AlwaysFailOpener(), sleep=lambda _: None, clock=lambda: next(ticks), max_cycles=10)

    out = capsys.readouterr().out
    drift = [e for e in _events(out) if e["event"] == "alert" and e["kind"] == "drift"]
    rate_alerts = [e for e in drift if "성공률" in e["reason"] and "불안정" in e["reason"]]
    # 3사 각각 창이 꽉 차 성공률 0% → 사이트별 1회씩(반복 안 함).
    assert sorted(e["site"] for e in rate_alerts) == sorted(s.name for s in hotdeal_boards())
    _assert_console_safe(out)
