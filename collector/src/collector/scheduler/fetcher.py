"""HTTP fetcher + robots 게이트 — 네트워크를 `Opener` 포트 하나로 격리한다.

포트로 격리하는 이유: 실 사이트 호출은 정지조건(CLAUDE.md)이고 `docs/21`은 실 네트워크 테스트를
금지한다. 그래서 판정 로직 전부를 fake opener로 덮고, 실제 소켓을 만지는 건 `urllib_opener` 3줄뿐이다.

크롤링 윤리(SEC-08)의 코드 구현:
  - **UA 위장 금지** — `USER_AGENT` 고정. 브라우저인 척하지 않는다.
  - **robots 존중** — Disallow면 요청하지 않는다. Crawl-delay가 우리 하한보다 길면 그쪽을 따른다
    (짧으면 우리 하한이 이긴다 — "설정으로 완화 불가").
  - **차단 신호 감지 시 자동 중지** — robots Disallow를 `403`으로 돌려 `classify_status`가 BLOCKED로
    보게 한다. 사이트가 중지되고 관리 알림이 뜨며 재시도하지 않는다. 접근이 금지됐다는 사실을
    "일시 장애"로 격하하지 않기 위한 매핑이다.
"""

from __future__ import annotations

import re
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import timedelta
from urllib.parse import urlsplit
from urllib.robotparser import RobotFileParser

from .loop import FetchResult, SiteSpec

USER_AGENT = "hogumeter/0.1 (personal use)"

# url → (status_code, body bytes). 실 네트워크는 이 포트 뒤에만 있다.
Opener = Callable[[str], "tuple[int, bytes]"]

_ROBOTS_DISALLOWED = 403


def urllib_opener(url: str, timeout: float = 10.0) -> tuple[int, bytes]:
    """실 HTTP GET. **테스트에서 호출 금지** — 이 함수만이 네트워크를 만진다.

    `urlopen`은 4xx·5xx에서 `HTTPError`를 던진다. 그대로 두면 `run_cycle._poll`이 예외를
    `TRANSIENT`로 삼켜 **`classify_status`가 403·429를 영원히 못 본다** — SEC-08의 자동 중지가
    죽고, 차단당한 사이트를 백오프하며 계속 두드리게 된다(원칙 5 위반).

    그래서 상태 코드는 **던지지 않고 돌려준다.** 포트 계약(`Opener`)이 `(status, bytes)`이고,
    fake opener는 처음부터 그렇게 행동했다 — 실 구현만 계약을 어기고 있었다.
    전송 실패(DNS·타임아웃)는 상태가 없으므로 예외로 남긴다 → 그건 진짜 `TRANSIENT`다.
    """
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:  # noqa: S310
            return response.status, response.read()
    except urllib.error.HTTPError as error:  # 상태가 있는 실패 — 판정은 classify_status의 몫
        return error.code, error.read()


@dataclass(frozen=True)
class _Rule:
    """robots.txt의 Allow/Disallow 한 줄. `pattern`은 원문(구체성 비교용), `regex`는 매칭용."""

    allow: bool
    pattern: str
    regex: re.Pattern[str]


def _compile_pattern(pattern: str) -> re.Pattern[str]:
    """robots 경로 패턴 → 정규식. `*`=임의 문자열, 끝의 `$`=끝 고정, 나머지는 리터럴.

    **stdlib(`urllib.robotparser`)은 이걸 못 한다** — 접두사 비교만 해서 `Disallow: /*view=`를
    "`/*view=`로 시작하는 경로"로 읽고 아무것도 막지 못한다(2026-07-22 실측: 루리웹이 금지한
    URL을 우리가 긁고 있었다). 그래서 판정을 stdlib에 맡기지 않는다.
    """
    anchored = pattern.endswith("$")
    body = pattern[:-1] if anchored else pattern
    expanded = "".join(".*" if ch == "*" else re.escape(ch) for ch in body)
    return re.compile("^" + expanded + ("$" if anchored else ""))


def _rules_for(text: str, agent: str) -> list[_Rule]:
    """우리 UA에 적용되는 그룹의 Allow/Disallow 목록. 우리를 이름으로 지목한 그룹이 있으면 그것만,
    없으면 `*` 그룹을 쓴다(표준 관행: 가장 구체적인 그룹 하나만 적용).
    """
    groups: list[tuple[set[str], list[_Rule]]] = []
    agents: set[str] = set()
    rules: list[_Rule] = []
    expecting_agent = True
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line or ":" not in line:
            continue
        name, _, value = line.partition(":")
        name, value = name.strip().lower(), value.strip()
        if name == "user-agent":
            if not expecting_agent:  # 규칙이 나온 뒤의 user-agent = 새 그룹
                groups.append((agents, rules))
                agents, rules = set(), []
            expecting_agent = True
            agents.add(value.lower())
        elif name in ("allow", "disallow"):
            expecting_agent = False
            if value:  # 빈 `Disallow:` = 제약 없음 → 규칙이 아니다
                rules.append(_Rule(name == "allow", value, _compile_pattern(value)))
    groups.append((agents, rules))

    lowered = agent.lower()
    named = [r for ags, rs in groups if any(a != "*" and a and a in lowered for a in ags) for r in rs]
    if named:
        return named
    return [r for ags, rs in groups if "*" in ags for r in rs]


def _path_allows(rules: list[_Rule], url: str) -> bool:
    """가장 **구체적인**(패턴이 긴) 매치가 이긴다. 같은 길이면 Allow가 이긴다(표준 관행).

    매칭 대상은 경로 + 쿼리다 — `Disallow: /*view=`는 `?view=thumbnail`을 겨냥한 규칙이라
    쿼리를 빼고 보면 영원히 안 걸린다.
    """
    parts = urlsplit(url)
    target = parts.path or "/"
    if parts.query:
        target += "?" + parts.query
    best: _Rule | None = None
    for rule in rules:
        if not rule.regex.match(target):
            continue
        if best is None or len(rule.pattern) > len(best.pattern) or (
            len(rule.pattern) == len(best.pattern) and rule.allow
        ):
            best = rule
    return True if best is None else best.allow


@dataclass(frozen=True)
class _RobotsDoc:
    """robots.txt 한 장 — 우리 매처용 규칙 + Crawl-delay 조회용 stdlib 파서."""

    rules: tuple[_Rule, ...]
    parser: RobotFileParser


@dataclass
class RobotsGate:
    """호스트별 robots.txt를 1회 조회해 캐시하고 판정한다.

    **판정을 stdlib에 맡기지 않는다** — `urllib.robotparser`는 와일드카드를 지원하지 않아
    `Disallow: /*view=` 같은 규칙을 통째로 무시한다(2026-07-22 실측). 차단 장치가 **미차단**하면
    다른 방어선이 없다(사이트 정책 위반은 우리가 유일한 방어선이다). Crawl-delay 조회만 stdlib을 쓴다.
    """

    opener: Opener
    _cache: dict[str, _RobotsDoc | None] = field(default_factory=dict)

    def _doc(self, url: str) -> _RobotsDoc | None:
        host = _origin(url)
        if host not in self._cache:
            self._cache[host] = self._load(host)
        return self._cache[host]

    def _load(self, origin: str) -> _RobotsDoc | None:
        try:
            status, body = self.opener(f"{origin}/robots.txt")
        except Exception:
            return None  # 조회 실패 시 제약 없음(표준 관행). 사이트 자체 장애는 fetch에서 드러난다.
        if status != 200:
            return None  # 404 등 → robots 없음 = 전체 허용
        text = body.decode("utf-8", errors="replace")
        parser = RobotFileParser()
        parser.parse(text.splitlines())
        return _RobotsDoc(rules=tuple(_rules_for(text, USER_AGENT)), parser=parser)

    def allows(self, url: str) -> bool:
        doc = self._doc(url)
        return True if doc is None else _path_allows(list(doc.rules), url)

    def crawl_delay(self, url: str) -> timedelta | None:
        doc = self._doc(url)
        if doc is None:
            return None
        delay = doc.parser.crawl_delay(USER_AGENT)
        return timedelta(seconds=float(delay)) if delay is not None else None


@dataclass(frozen=True)
class HttpFetcher:
    """`run_cycle`이 주입받는 fetch 포트의 실 구현."""

    opener: Opener
    robots: RobotsGate

    def __call__(self, spec: SiteSpec) -> FetchResult:
        if not self.robots.allows(spec.url):
            # 접근 금지 = 차단 신호. TRANSIENT로 격하하면 백오프하며 계속 두드린다.
            return FetchResult(status_code=_ROBOTS_DISALLOWED, body="")

        status, body = self.opener(spec.url)
        if status != 200:
            return FetchResult(status_code=status, body="")

        # strict 디코딩: errors="replace"로 덮으면 제목이 조용히 깨진다(뽐뿌 cp949 교훈).
        # 실패는 예외 → run_cycle이 TRANSIENT로 격리한다.
        return FetchResult(status_code=status, body=body.decode(spec.encoding))


def effective_interval_with_robots(spec: SiteSpec, robots: RobotsGate) -> timedelta:
    """robots의 Crawl-delay가 우리 주기보다 길면 그쪽을 따른다. 짧으면 우리가 이긴다."""
    declared = robots.crawl_delay(spec.url)
    return max(spec.interval, declared) if declared else spec.interval


def _origin(url: str) -> str:
    parts = urlsplit(url)
    return f"{parts.scheme}://{parts.netloc}"
