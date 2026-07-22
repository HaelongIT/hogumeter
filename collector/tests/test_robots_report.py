"""실 robots.txt 대조 리포트 — `pre-deploy §F`가 사람에게 시키는 그 일의 도구.

이 테스트는 fake opener만 쓴다(실 네트워크 금지). 도구가 실제로 나가는 것은
`ALLOW_REAL_ROBOTS=1`일 때뿐이고, 그건 `scripts/check-robots-drill.sh`가 로컬 서버로 시험한다.
"""

from datetime import datetime, timezone

import pytest

from collector.scheduler.loop import SiteSpec
from collector.scheduler.policy import SiteKind
from collector.scheduler.sites import hotdeal_boards
from collector.tools.robots_report import RobotsFinding, format_field_notes, report

NOW = datetime(2026, 7, 10, 12, 0, tzinfo=timezone.utc)


def _spec(name: str, url: str) -> SiteSpec:
    from datetime import timedelta

    return SiteSpec(name=name, kind=SiteKind.BOARD, interval=timedelta(seconds=60), url=url,
                    encoding="utf-8", parse=lambda body, now: [])


PPOMPPU = _spec("ppomppu", "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu")


def opener_for(status: int, body: str):
    def opener(url: str):
        assert url.endswith("/robots.txt"), f"robots.txt 말고 다른 걸 요청했다: {url}"
        return (status, body.encode("utf-8"))

    return opener


def test_disallow_on_our_path_is_reported_as_blocked():
    """Disallow면 그 사이트는 자동 중지되고 D-3 없이는 되살릴 수 없다 — 가장 중요한 판정."""
    (finding,) = report([PPOMPPU], opener_for(200, "User-agent: *\nDisallow: /zboard/\n"))

    assert finding.site == "ppomppu"
    assert finding.allowed is False
    assert finding.robots_status == 200


def test_allow_is_reported_as_allowed():
    (finding,) = report([PPOMPPU], opener_for(200, "User-agent: *\nDisallow: /admin/\n"))

    assert finding.allowed is True


def test_crawl_delay_is_reported_in_seconds():
    """Crawl-delay가 우리 하한(60s)보다 크면 그쪽을 따라야 한다(SEC-08)."""
    (finding,) = report([PPOMPPU], opener_for(200, "User-agent: *\nCrawl-delay: 120\n"))

    assert finding.crawl_delay_seconds == 120.0


def test_no_crawl_delay_is_none_not_zero():
    """0초와 '선언 없음'은 다른 사건이다."""
    (finding,) = report([PPOMPPU], opener_for(200, "User-agent: *\nDisallow:\n"))

    assert finding.crawl_delay_seconds is None


def test_missing_robots_means_allowed_and_status_is_kept():
    """404 = robots 없음 = 전체 허용(표준 관행). 그래도 상태를 숨기지 않는다."""
    (finding,) = report([PPOMPPU], opener_for(404, ""))

    assert finding.allowed is True
    assert finding.robots_status == 404


def test_fetch_failure_is_reported_not_swallowed():
    """조회 실패를 '허용'으로만 적으면 사람이 그걸 확인했다고 착각한다."""

    def broken(url: str):
        raise OSError("연결 거부")

    (finding,) = report([PPOMPPU], broken)

    assert finding.robots_status is None
    assert finding.error is not None and "연결 거부" in finding.error


def test_every_site_is_reported_even_if_one_fails():
    ruliweb = _spec("ruliweb", "https://bbs.ruliweb.com/market/board/1020")

    def flaky(url: str):
        if "ruliweb" in url:
            raise OSError("타임아웃")
        return (200, b"User-agent: *\nDisallow: /\n")

    findings = report([PPOMPPU, ruliweb], flaky)

    assert [f.site for f in findings] == ["ppomppu", "ruliweb"]
    assert findings[0].allowed is False
    assert findings[1].robots_status is None


def test_field_notes_block_is_ascii_safe_markdown():
    """docs/98에 그대로 붙일 블록. 콘솔로도 나가므로 ASCII로 인코딩 가능해야 한다."""
    findings = [
        RobotsFinding("ppomppu", "https://x/1", allowed=False, crawl_delay_seconds=None,
                      robots_status=200, error=None),
        RobotsFinding("ruliweb", "https://y/2", allowed=True, crawl_delay_seconds=10.0,
                      robots_status=200, error=None),
    ]

    block = format_field_notes(findings, NOW)

    assert "2026-07-10" in block
    assert "ppomppu" in block and "DISALLOW" in block
    assert "crawl-delay=10.0s" in block
    block.encode("cp949")  # Windows 콘솔에서 죽지 않는다


@pytest.mark.parametrize("status", [403, 429])
def test_blocked_status_on_robots_is_surfaced(status):
    """403/429는 차단 신호다(SEC-08). robots 조회에서 나오면 그것부터 사람이 봐야 한다."""
    (finding,) = report([PPOMPPU], opener_for(status, ""))

    assert finding.robots_status == status
    assert finding.blocked_signal is True


# ── 진입점: 실 네트워크는 opt-in 없이는 나가지 않는다 ────────────────────

from collector.tools.robots_report import ALLOW_REAL_ROBOTS_ENV, main  # noqa: E402


class RecordingOpener:
    def __init__(self):
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        return (200, b"User-agent: *\nDisallow: /admin/\n")


def test_entrypoint_refuses_without_optin(monkeypatch, capsys):
    """`.claude/hooks/guard.sh`는 스크립트 안의 호출을 못 본다 — 도구가 스스로 막는다(docs/91 Q-60)."""
    monkeypatch.delenv(ALLOW_REAL_ROBOTS_ENV, raising=False)
    opener = RecordingOpener()

    assert main(opener=opener, clock=lambda: NOW) == 0
    assert opener.calls == []  # 단 한 번도 나가지 않았다
    out = capsys.readouterr().out
    assert "REFUSED reason=real_network_opt_in_missing" in out  # 문구가 아니라 마커
    assert ALLOW_REAL_ROBOTS_ENV in out
    out.encode("ascii")  # 거부 경로는 순수 ASCII — 어떤 콘솔에서도 읽힌다


def test_entrypoint_with_optin_reports_every_board(monkeypatch, capsys):
    monkeypatch.setenv(ALLOW_REAL_ROBOTS_ENV, "1")
    opener = RecordingOpener()

    assert main(opener=opener, clock=lambda: NOW) == 0

    out = capsys.readouterr().out
    # 레지스트리가 정본이다 — 폴링 대상은 robots 실측에 따라 바뀐다(2026-07-22: 뽐뿌 1사).
    for spec in hotdeal_boards():
        assert f"- {spec.name}: ALLOW" in out
    assert all(url.endswith("/robots.txt") for url in opener.calls)


def test_entrypoint_exits_nonzero_when_a_board_is_disallowed(monkeypatch):
    """DISALLOW는 '확인 완료'가 아니라 '사람이 결정해야 함'이다. 조용히 성공하지 않는다."""
    monkeypatch.setenv(ALLOW_REAL_ROBOTS_ENV, "1")

    def disallow(url: str):
        return (200, b"User-agent: *\nDisallow: /\n")

    assert main(opener=disallow, clock=lambda: NOW) == 1
