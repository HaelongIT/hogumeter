"""엔트리포인트 — 실 네트워크는 환경변수 opt-in 없이는 절대 나가지 않는다.

정지조건("실사이트 크롤링")을 산문이 아니라 기계로 강제한다. 이 테스트가 그 기계를 지킨다.
"""

import json
from datetime import datetime, timedelta, timezone

from collector.__main__ import ALLOW_NETWORK_ENV, main
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
