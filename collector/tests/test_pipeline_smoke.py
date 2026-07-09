"""종단 스모크 — fixture 바이트 → fetch → 디코딩 → 파싱 → 정규화 → RawDealRecord.

collector의 부품(파서·스케줄러·ingest)은 각자 GREEN이었지만 **함께 돌려본 적이 없었다.**
그 틈으로 어휘 불일치가 잠복했다 — `parse_bunjang`의 `ENDED`는 `to_raw_records`가 거부하는
값이었다(docs/91 Q-41). 이 파일은 "통합 테스트"가 아니라 **계약 검증**이다.

실 네트워크 0: opener를 fake로 주입한다(`docs/21` 실 사이트 호출 테스트 금지).
"""

from datetime import datetime, timedelta, timezone
from pathlib import Path

from collector.pipeline.ingest import to_raw_records
from collector.scheduler.fetcher import HttpFetcher, RobotsGate
from collector.scheduler.loop import run_cycle
from collector.scheduler.policy import BackoffPolicy
from collector.scheduler.sites import hotdeal_boards

FIXTURES = Path(__file__).parent / "fixtures"
# 2026-07-09 23:00 KST. fixture의 최신 글(21:10 KST)보다 뒤여야 postedAt이 과거로 해석된다.
NOW = datetime(2026, 7, 9, 14, 0, tzinfo=timezone.utc)
BACKOFF = BackoffPolicy(base=timedelta(seconds=60), factor=2, cap=timedelta(minutes=30))

# 파서 골든이 단언한 건수. 종단에서도 같은 수가 나와야 한다.
EXPECTED = {"ppomppu": 21, "ruliweb": 28, "fmkorea": 20}
TOTAL = sum(EXPECTED.values())

_FIXTURE_OF = {
    "ppomppu": "ppomppu/list_normal.html",
    "ruliweb": "ruliweb/list_normal.html",
    "fmkorea": "fmkorea/list_normal.html",
}


class FakeOpener:
    """사이트 URL → fixture **바이트 그대로**. 디코딩은 fetcher의 몫이다."""

    def __init__(self, overrides: dict | None = None):
        self.calls: list[str] = []
        self._overrides = overrides or {}
        self._by_url = {
            spec.url: FIXTURES.joinpath(_FIXTURE_OF[spec.name]).read_bytes()
            for spec in hotdeal_boards()
        }

    def __call__(self, url: str):
        self.calls.append(url)
        if url in self._overrides:
            return self._overrides[url]
        if url.endswith("/robots.txt"):
            return (404, b"")  # robots 없음 = 전체 허용
        return (200, self._by_url[url])


def _run(opener: FakeOpener, states=None, now=NOW):
    fetcher = HttpFetcher(opener=opener, robots=RobotsGate(opener=opener))
    return run_cycle(hotdeal_boards(), states or {}, now, fetcher, BACKOFF)


def test_end_to_end_produces_raw_deal_records():
    opener = FakeOpener()

    result = _run(opener)
    records = to_raw_records(result.deals, NOW)

    assert len(result.deals) == TOTAL
    assert len(records) == TOTAL  # 자연키 중복 없음
    assert result.alerts == []
    assert {r.site for r in records} == set(EXPECTED)
    assert all(r.captured_at == NOW for r in records)
    assert all(r.status in {"ACTIVE", "SOLD_OUT", "DELETED"} for r in records)


def test_each_site_is_fetched_once_with_its_own_encoding():
    opener = FakeOpener()

    result = _run(opener)

    page_calls = [u for u in opener.calls if not u.endswith("/robots.txt")]
    assert len(page_calls) == 3
    counts = {}
    for d in result.deals:
        counts[d.site] = counts.get(d.site, 0) + 1
    assert counts == EXPECTED


def test_posted_at_is_resolved_for_every_board_deal():
    """core는 `firstSeen = postedAt ?? capturedAt`. 여기가 비면 3일 전 글도 '방금 발생'이 된다.

    목록 시각은 "당일 21:10" 형태라 폴링 시각(now)이 있어야 해석된다 — run_cycle이 넘긴다.
    """
    result = _run(FakeOpener())

    assert all(d.posted_at is not None for d in result.deals)
    assert all(d.posted_at <= NOW for d in result.deals)

    records = to_raw_records(result.deals, NOW)
    assert all(r.posted_at is not None for r in records)


def test_ppomppu_survives_cp949_decoding_end_to_end():
    """레지스트리가 cp949를 들고 있지 않으면 여기서 깨진다(선언은 euc-kr)."""
    result = _run(FakeOpener())

    titles = [d.title for d in result.deals if d.site == "ppomppu"]
    assert any("옥션" in t for t in titles)
    assert all("�" not in t for t in titles)  # replace 문자가 섞이지 않았다


def test_repeated_cycle_yields_identical_records():
    """멱등: 같은 페이지를 다시 수집해도 (site, post_id) 레코드가 그대로다(REL-01)."""
    opener = FakeOpener()
    first = to_raw_records(_run(opener).deals, NOW)

    later = NOW + timedelta(seconds=120)  # 하한 경과 → 다시 due
    states = {s.site: s for s in _run(opener).states.values()}
    second = to_raw_records(_run(opener, states, later).deals, later)

    key = lambda r: (r.site, r.post_id)  # noqa: E731
    assert sorted(map(key, first)) == sorted(map(key, second))
    assert {key(r): r.headline_price for r in first} == {key(r): r.headline_price for r in second}


def test_one_site_failing_does_not_stop_the_others():
    """REL-02 격리 — 펨코가 500을 줘도 뽐뿌·루리웹은 정상 수집된다."""
    fmkorea = next(s for s in hotdeal_boards() if s.name == "fmkorea")
    opener = FakeOpener(overrides={fmkorea.url: (500, b"")})

    result = _run(opener)

    assert len(result.deals) == TOTAL - EXPECTED["fmkorea"]
    assert result.states["fmkorea"].consecutive_failures == 1
    assert result.states["fmkorea"].stopped is False  # 일시 장애 → 백오프
    assert result.states["ppomppu"].last_successful_poll == NOW


def test_robots_disallow_stops_only_that_site():
    """뽐뿌만 금지 → 뽐뿌만 중지 + Alert 1건. 나머지는 계속 돈다."""
    ppomppu = next(s for s in hotdeal_boards() if s.name == "ppomppu")
    robots_url = "https://www.ppomppu.co.kr/robots.txt"
    opener = FakeOpener(overrides={robots_url: (200, b"User-agent: *\nDisallow: /zboard\n")})

    result = _run(opener)

    assert ppomppu.url not in opener.calls  # 요청 자체를 하지 않는다
    assert result.states["ppomppu"].stopped is True
    assert [a.site for a in result.alerts] == ["ppomppu"]
    assert len(result.deals) == TOTAL - EXPECTED["ppomppu"]
    assert result.states["ruliweb"].last_successful_poll == NOW
