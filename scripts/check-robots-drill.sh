#!/usr/bin/env bash
# `scripts/check-robots.sh`의 리허설 — **실 사이트를 만지지 않는다.**
#
#   bash scripts/check-robots-drill.sh
#
# 왜 필요한가: `RobotsGate`는 fake opener 테스트만 통과했다. 실제 소켓을 만지는 `urllib_opener`를
# 태우는 경로는 아무도 돌려본 적이 없다. 그런데 그 경로는 **실 수집을 켜기 직전에 처음 실행된다** —
# 복구 스크립트와 같은 종류의 위험이다(docs/99: 리허설로만 존재를 증명한다).
#
# 설계: 서버를 **검사와 같은 프로세스의 스레드**로 띄우고 **임시 포트(0)**에 바인딩한다.
# 별도 프로세스를 백그라운드로 돌리면 `kill $!`가 서브셸만 죽이고 python 자식은 살아남아
# 포트를 문 채 빈 디렉토리를 서빙한다(실제로 그랬다). 프로세스가 없으면 좀비도 없다.
#
# 검증: ① opt-in 없이는 한 번도 나가지 않는다 ② Disallow를 실제로 읽어 차단으로 판정
#       ③ Crawl-delay를 초 단위로 읽는다 ④ robots.txt가 없으면(404) 허용
#       ⑤ **와일드카드**(`Disallow: /*view=`)를 실제로 막는다 + 안 걸리는 주소는 통과(오차단 방지)
#          — 2026-07-22에 이 문법을 못 막아 금지 URL을 긁었다. 드릴 입력을 우리가 쓰는 한
#            우리가 아는 문법만 시험하므로, 실제로 당한 문법을 박아 둔다(docs/99).

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)

fail() {
	echo "FAIL: $*" >&2
	exit 1
}

echo "--- 1) opt-in 없이는 나가지 않는다 (스크립트 자신의 게이트) ---"
out=$(cd "$root" && bash scripts/check-robots.sh)
# 문구가 아니라 ASCII 마커를 본다. 한글은 콘솔 인코딩에 따라 깨지고, 그걸 grep하면 도구가 굳는다.
echo "$out" | grep -q "REFUSED reason=real_network_opt_in_missing" || fail "거부 마커가 없다: $out"
echo "$out" | grep -q "ALLOW_REAL_ROBOTS" || fail "opt-in 안내가 없다: $out"

echo "--- 2~4) 운영과 같은 경로(urllib_opener -> RobotsGate)를 로컬 서버에 태운다 ---"
cd "$root/collector"
uv run python - <<'PY'
import http.server
import tempfile
import threading
from datetime import timedelta
from pathlib import Path

from collector.scheduler.fetcher import urllib_opener
from collector.scheduler.loop import SiteSpec
from collector.scheduler.policy import SiteKind
from collector.tools.robots_report import report

root = Path(tempfile.mkdtemp())
with_robots = root / "with"
without_robots = root / "without"
wildcard_robots = root / "wildcard"
with_robots.mkdir()
without_robots.mkdir()
wildcard_robots.mkdir()
(with_robots / "robots.txt").write_text("User-agent: *\nDisallow: /zboard/\nCrawl-delay: 120\n", encoding="utf-8")
# 와일드카드 규칙 — 2026-07-22에 실제로 우리를 물었다. urllib.robotparser가 이걸 무시해
# 루리웹이 금지한 `?view=` URL을 긁고 있었다. 드릴의 robots를 우리가 쓰는 한, 우리가 아는
# 문법만 시험하게 된다 — 그래서 실제로 당한 문법을 여기 박아 둔다(docs/99 2026-07-22).
(wildcard_robots / "robots.txt").write_text("User-agent: *\nDisallow: /*view=\n", encoding="utf-8")


def check(label, condition, detail):
    # 출력은 순수 ASCII다. 한글은 콘솔 인코딩에 따라 깨지고, 그러면 로그가 읽히지 않는다.
    if not condition:
        raise SystemExit(f"FAIL: {label} ({detail})")
    print(f"    ok  {label}")


def probe(directory, path="/zboard/list.php"):
    """디렉토리 하나를 서빙하는 서버를 띄우고 운영과 같은 경로로 한 번 조회한다.

    파일을 지우지 않고 **디렉토리를 갈아끼운다** — Windows는 서버가 잡고 있는 파일을 못 지운다.
    포트 0 = OS가 빈 포트를 준다. 고정 포트는 좀비가 물고 있으면 조용히 남의 디렉토리를 서빙한다.
    """

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *a, **kw):
            super().__init__(*a, directory=str(directory), **kw)

        def log_message(self, *_):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        spec = SiteSpec(name="local", kind=SiteKind.BOARD, interval=timedelta(seconds=60),
                        url=f"http://127.0.0.1:{port}{path}", encoding="utf-8",
                        parse=lambda body, now: [])
        (finding,) = report([spec], urllib_opener)
        return finding
    finally:
        server.shutdown()
        server.server_close()


found = probe(with_robots)
check("reads Disallow over a real socket -> blocked", found.allowed is False, found)
check("surfaces robots.txt status to the human", found.robots_status == 200, found)
check("reads Crawl-delay in seconds", found.crawl_delay_seconds == 120.0, found)
check("no error on a successful fetch", found.error is None, found)

# 와일드카드: 실제로 우리를 문 문법. 차단과 **오차단 방지**를 함께 본다.
blocked = probe(wildcard_robots, "/market/board/1020?view=thumbnail&page=1")
check("wildcard Disallow /*view= over a real socket -> blocked", blocked.allowed is False, blocked)
allowed = probe(wildcard_robots, "/market/board/1020?page=1")
check("same host without the disallowed param -> allowed (no over-block)", allowed.allowed is True, allowed)

missing = probe(without_robots)
check("missing robots.txt (404) -> allowed", missing.allowed is True, missing)
check("404 status is not hidden", missing.robots_status == 404, missing)
PY

echo
echo "ROBOTS DRILL PASS: opt-in 게이트 -> Disallow 판정 -> Crawl-delay -> 404 허용 -> 와일드카드 차단/오차단방지"
