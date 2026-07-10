"""HTTP fetcher + robots 게이트. 실 네트워크 0 — opener를 fake로 주입한다."""

from datetime import timedelta

import pytest

from collector.scheduler.fetcher import (
    USER_AGENT,
    HttpFetcher,
    RobotsGate,
    effective_interval_with_robots,
)
from collector.scheduler.loop import SiteSpec
from collector.scheduler.policy import Outcome, SiteKind, classify_status
from collector.scheduler.sites import hotdeal_boards


class FakeOpener:
    """url → (status, bytes). 호출 기록을 남긴다."""

    def __init__(self, responses: dict):
        self._responses = responses
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        if url not in self._responses:
            raise FileNotFoundError(url)
        response = self._responses[url]
        if isinstance(response, Exception):
            raise response
        return response


SITE = "https://example.test"


def _spec(encoding: str = "utf-8", url: str = f"{SITE}/hotdeal") -> SiteSpec:
    return SiteSpec(
        name="example",
        kind=SiteKind.BOARD,
        interval=timedelta(seconds=60),
        url=url,
        encoding=encoding,
        parse=lambda body, now: [],
    )


def _fetcher(responses: dict) -> tuple[HttpFetcher, FakeOpener]:
    opener = FakeOpener(responses)
    return HttpFetcher(opener=opener, robots=RobotsGate(opener=opener)), opener


# ── robots 게이트 ───────────────────────────────────────────────────────


def test_robots_absent_means_allowed():
    """robots.txt가 404면 제약 없음(표준 관행)."""
    fetcher, opener = _fetcher(
        {f"{SITE}/robots.txt": (404, b""), f"{SITE}/hotdeal": (200, b"<html/>")}
    )

    assert fetcher(_spec()).status_code == 200
    assert f"{SITE}/hotdeal" in opener.calls


def test_robots_disallow_maps_to_blocked_not_transient():
    """Disallow = 접근 금지. 일시 장애로 격하하면 백오프하며 계속 두드리게 된다."""
    fetcher, opener = _fetcher(
        {f"{SITE}/robots.txt": (200, b"User-agent: *\nDisallow: /hotdeal\n")}
    )

    result = fetcher(_spec())

    assert result.status_code == 403
    assert classify_status(result.status_code) is Outcome.BLOCKED  # → 사이트 자동 중지 + Alert
    assert f"{SITE}/hotdeal" not in opener.calls  # 요청 자체를 하지 않는다


def test_robots_allows_other_paths():
    fetcher, opener = _fetcher(
        {
            f"{SITE}/robots.txt": (200, b"User-agent: *\nDisallow: /admin\n"),
            f"{SITE}/hotdeal": (200, b"<html/>"),
        }
    )

    assert fetcher(_spec()).status_code == 200


def test_robots_is_fetched_once_per_host():
    fetcher, opener = _fetcher(
        {f"{SITE}/robots.txt": (200, b""), f"{SITE}/hotdeal": (200, b"<html/>")}
    )

    fetcher(_spec())
    fetcher(_spec())

    assert opener.calls.count(f"{SITE}/robots.txt") == 1


def test_robots_fetch_failure_does_not_block_collection():
    fetcher, _ = _fetcher(
        {f"{SITE}/robots.txt": ConnectionError("down"), f"{SITE}/hotdeal": (200, b"<html/>")}
    )

    assert fetcher(_spec()).status_code == 200


# ── Crawl-delay: 길면 따르고, 짧으면 우리 하한이 이긴다 (SEC-08) ────────


def test_longer_crawl_delay_wins():
    opener = FakeOpener({f"{SITE}/robots.txt": (200, b"User-agent: *\nCrawl-delay: 120\n")})

    assert effective_interval_with_robots(_spec(), RobotsGate(opener=opener)) == timedelta(seconds=120)


def test_shorter_crawl_delay_does_not_lower_our_floor():
    opener = FakeOpener({f"{SITE}/robots.txt": (200, b"User-agent: *\nCrawl-delay: 5\n")})

    assert effective_interval_with_robots(_spec(), RobotsGate(opener=opener)) == timedelta(seconds=60)


def test_no_crawl_delay_keeps_our_interval():
    opener = FakeOpener({f"{SITE}/robots.txt": (404, b"")})

    assert effective_interval_with_robots(_spec(), RobotsGate(opener=opener)) == timedelta(seconds=60)


# ── 응답 처리 ───────────────────────────────────────────────────────────


def test_decodes_body_with_site_encoding():
    """뽐뿌는 cp949. 잘못된 코덱으로 열면 조용히 깨지는 게 아니라 터져야 한다."""
    body = "[옥션]소가죽 벨트".encode("cp949")
    fetcher, _ = _fetcher({f"{SITE}/robots.txt": (404, b""), f"{SITE}/hotdeal": (200, body)})

    assert fetcher(_spec(encoding="cp949")).body == "[옥션]소가죽 벨트"


def test_wrong_encoding_raises_rather_than_corrupting():
    body = "[옥션]소가죽 벨트".encode("cp949")
    fetcher, _ = _fetcher({f"{SITE}/robots.txt": (404, b""), f"{SITE}/hotdeal": (200, body)})

    with pytest.raises(UnicodeDecodeError):  # run_cycle이 TRANSIENT로 격리한다
        fetcher(_spec(encoding="utf-8"))


@pytest.mark.parametrize("status", [403, 429, 500, 503])
def test_non_200_is_passed_through_for_classification(status):
    fetcher, _ = _fetcher({f"{SITE}/robots.txt": (404, b""), f"{SITE}/hotdeal": (status, b"")})

    assert fetcher(_spec()).status_code == status


def test_user_agent_is_not_disguised():
    """절대 원칙 5: UA 위장 금지. 브라우저인 척하지 않는다."""
    assert "hogumeter" in USER_AGENT
    assert "Mozilla" not in USER_AGENT


# ── 레지스트리 ──────────────────────────────────────────────────────────


def test_registry_is_the_three_hotdeal_boards():
    specs = hotdeal_boards()

    assert [s.name for s in specs] == ["ppomppu", "ruliweb", "fmkorea"]
    assert all(s.kind is SiteKind.BOARD for s in specs)
    assert all(s.url.startswith("https://") for s in specs)
    assert all(s.interval == timedelta(seconds=60) for s in specs)  # PERF-05


def test_registry_carries_ppomppu_cp949_encoding():
    """선언(charset=euc-kr)이 아니라 실측(cp949)을 따른다 — docs/98."""
    ppomppu = next(s for s in hotdeal_boards() if s.name == "ppomppu")

    assert ppomppu.encoding == "cp949"


def test_registry_excludes_bunjang():
    """번개는 검색어별 URL이라 UsedSearch(M2) 모델이 선행한다."""
    assert "bunjang" not in [s.name for s in hotdeal_boards()]


def test_registry_returns_a_fresh_list():
    assert hotdeal_boards() is not hotdeal_boards()


# ── Opener 포트 계약: 상태를 **돌려준다**. 예외로 바꾸지 않는다 ──────────────
#
# `urlopen`은 4xx·5xx에서 HTTPError를 던진다. 그걸 그대로 두면 `_poll`이 예외를 TRANSIENT로
# 삼켜 **`classify_status`가 403/429를 영원히 못 본다** — SEC-08의 자동 중지가 죽는다.
# fake opener는 `(403, b"")`를 돌려주는데 실 opener는 던졌다. 부품별 GREEN이 경계에서 거짓말한 것.

import urllib.error  # noqa: E402
import urllib.request  # noqa: E402

from collector.scheduler.fetcher import urllib_opener  # noqa: E402


def _raise_http_error(status: int, body: bytes = b""):
    def fake_urlopen(request, timeout=None):
        raise urllib.error.HTTPError(request.full_url, status, "blocked", {}, io.BytesIO(body))

    return fake_urlopen


import io  # noqa: E402


@pytest.mark.parametrize("status", [403, 429, 404, 500])
def test_urllib_opener_returns_status_instead_of_raising(monkeypatch, status):
    monkeypatch.setattr(urllib.request, "urlopen", _raise_http_error(status, b"nope"))

    assert urllib_opener("https://example.invalid/x") == (status, b"nope")


def test_urllib_opener_still_raises_on_transport_failures(monkeypatch):
    """DNS·타임아웃은 상태가 없다. 이건 예외로 남아 TRANSIENT가 된다(재시도 대상)."""

    def boom(request, timeout=None):
        raise urllib.error.URLError("연결 거부")

    monkeypatch.setattr(urllib.request, "urlopen", boom)

    with pytest.raises(urllib.error.URLError):
        urllib_opener("https://example.invalid/x")


def test_block_signal_reaches_classify_status():
    """403은 BLOCKED여야 한다 — 이 경로가 프로덕션에서 실제로 이어지는지 좁게 관통한다."""
    from collector.scheduler.policy import Outcome, classify_status

    status, _ = urllib_opener_stub_403()
    assert classify_status(status) is Outcome.BLOCKED


def urllib_opener_stub_403():
    return (403, b"")
