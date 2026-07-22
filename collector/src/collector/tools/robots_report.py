"""실 robots.txt 대조 리포트 (SEC-08, pre-deploy §F).

`RobotsGate`는 이미 구현·테스트돼 있지만 **fake opener로만 통과했다.** 실 3사의 robots.txt가
우리 리스트 URL을 Disallow하는지, Crawl-delay를 선언하는지는 아무도 본 적이 없다. 이 도구는
그 1회 조회를 명령 하나로 만든다 — 결과는 `docs/98-field-notes.md`에 붙일 블록으로 나온다.

판정은 새로 만들지 않는다. `scheduler.fetcher.RobotsGate`(stdlib `RobotFileParser`)를 그대로 쓴다.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from ..scheduler.fetcher import RobotsGate, Opener
from ..scheduler.loop import SiteSpec

# 차단 신호(SEC-08). robots 조회에서 이게 나오면 수집 전에 사람이 먼저 봐야 한다.
_BLOCKED_STATUSES = frozenset({403, 429})


@dataclass(frozen=True)
class RobotsFinding:
    """사이트 하나의 대조 결과. '허용'과 '확인 못 함'을 절대 섞지 않는다."""

    site: str
    url: str
    allowed: bool
    crawl_delay_seconds: float | None
    robots_status: int | None  # None = 조회 자체가 실패했다
    error: str | None

    @property
    def blocked_signal(self) -> bool:
        return self.robots_status in _BLOCKED_STATUSES


def report(specs: list[SiteSpec], opener: Opener) -> list[RobotsFinding]:
    """사이트별로 robots.txt를 1회 조회한다. 한 사이트의 실패가 나머지를 막지 않는다(REL-02의 정신)."""
    return [_check(spec, opener) for spec in specs]


def _check(spec: SiteSpec, opener: Opener) -> RobotsFinding:
    status: int | None = None
    error: str | None = None

    # 상태 코드는 게이트가 감춘다(404도 None으로 접는다). 사람에겐 그 숫자가 필요하다.
    try:
        status, _ = opener(_robots_url(spec.url))
    except Exception as failure:  # noqa: BLE001 — 무엇이 터졌는지 그대로 사람에게 넘긴다
        error = f"{type(failure).__name__}: {failure}"

    gate = RobotsGate(opener=opener)
    delay = gate.crawl_delay(spec.url)
    return RobotsFinding(
        site=spec.name,
        url=spec.url,
        allowed=gate.allows(spec.url),
        crawl_delay_seconds=delay.total_seconds() if delay is not None else None,
        robots_status=status,
        error=error,
    )


def _robots_url(url: str) -> str:
    from urllib.parse import urlsplit

    parts = urlsplit(url)
    return f"{parts.scheme}://{parts.netloc}/robots.txt"


def format_field_notes(findings: list[RobotsFinding], at: datetime) -> str:
    """`docs/98-field-notes.md`에 그대로 붙일 블록. 콘솔로도 나가므로 ASCII만 쓴다."""
    lines = [f"### {at.date().isoformat()} robots.txt 실 대조 (SEC-08)", ""]
    for f in findings:
        if f.error is not None:
            lines.append(f"- {f.site}: FETCH-FAILED ({f.error}) - 허용 여부를 확인하지 못했다")
            continue
        verdict = "ALLOW" if f.allowed else "DISALLOW"
        delay = "crawl-delay=none" if f.crawl_delay_seconds is None else f"crawl-delay={f.crawl_delay_seconds}s"
        blocked = " BLOCKED-SIGNAL" if f.blocked_signal else ""
        lines.append(f"- {f.site}: {verdict} status={f.robots_status} {delay}{blocked}")
        lines.append(f"  - url: {f.url}")
    lines.append("")
    lines.append("> DISALLOW면 그 사이트는 자동 중지되며 재개 경로(decisions-needed D-3) 없이는 되살릴 수 없다.")
    lines.append("> Crawl-delay가 우리 하한(60s)보다 크면 그쪽을 따른다 - 하한은 낮추지 않는다(SEC-08).")
    return "\n".join(lines)


ALLOW_REAL_ROBOTS_ENV = "ALLOW_REAL_ROBOTS"


def main(*, opener=None, clock=None) -> int:
    """운영자용 진입점. **opt-in 없이는 한 번도 나가지 않는다.**

    `.claude/hooks/guard.sh`는 Bash 명령 문자열만 본다 — `bash scripts/check-robots.sh` 안의
    네트워크 호출은 훅에 보이지 않는다(docs/91 Q-60). 그래서 도구가 스스로 게이트를 건다.
    collector의 `COLLECTOR_ALLOW_NETWORK`와 같은 패턴이다.

    반환: DISALLOW가 하나라도 있으면 1. "확인 완료"가 아니라 "사람이 결정해야 함"이기 때문이다.
    """
    import os
    from datetime import timezone

    if os.environ.get(ALLOW_REAL_ROBOTS_ENV) != "1":
        # 문구가 아니라 마커를 낸다 — 한글은 콘솔 인코딩에 따라 깨지고, 그걸 grep하면 도구가 굳는다.
        print(f"REFUSED reason=real_network_opt_in_missing env={ALLOW_REAL_ROBOTS_ENV}")
        print(f"  {ALLOW_REAL_ROBOTS_ENV}=1 bash scripts/check-robots.sh")
        print("  scope: robots.txt of each candidate host, one request each. No list pages.")
        return 0

    from ..scheduler.fetcher import urllib_opener
    from ..scheduler.sites import robots_check_targets

    now = (clock or (lambda: datetime.now(timezone.utc)))()
    findings = report(robots_check_targets(), opener or urllib_opener)

    print(format_field_notes(findings, now))
    print()
    print("위 블록을 docs/98-field-notes.md에 붙이고, pre-deploy §F 항목을 갱신하세요.")

    disallowed = [f.site for f in findings if not f.allowed]
    if disallowed:
        print(f"\nDISALLOW: {', '.join(disallowed)} - 재개 경로(decisions-needed D-3)가 없으면 수집을 켜지 마세요.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
