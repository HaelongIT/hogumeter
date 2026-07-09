"""OBS-01 구조화 로그 · OBS-02 카운터 — 순수 함수(시각은 주입, 출력은 호출자).

실 폴링을 켜면 사람은 `docker logs`만 본다. 사이클마다 무엇을 몇 건 수집했고 무엇이 실패했는지가
**기계로 읽히는 한 줄**이어야 한다(JSON Lines).

한글은 `ensure_ascii`로 `\\uXXXX`로 뺀다. 값은 온전하되 출력은 순수 ASCII라 **어떤 콘솔 인코딩에서도
죽지 않는다** — em dash 하나로 엔트리포인트가 죽은 적이 있다(docs/99 2026-07-09).

한계: OBS-01의 "상관 ID(딜 단위 추적)"는 아직 없다. 딜 단위 추적은 `deal_event`를 만드는 core의
관심사이고, collector는 사이클·사이트 단위 카운터까지만 낸다.
"""

from __future__ import annotations

import json
from datetime import datetime

from .scheduler.loop import CycleResult
from .scheduler.policy import Outcome


def event(name: str, at: datetime, **fields) -> str:
    """구조화 로그 한 줄. `None` 필드는 뺀다 — "값 없음"은 남길 사실이 아니다."""
    payload = {"ts": at.isoformat(), "event": name}
    payload.update({key: value for key, value in fields.items() if value is not None})
    return json.dumps(payload, ensure_ascii=True, separators=(",", ":"))


def counters(result: CycleResult) -> dict:
    """OBS-02 핵심 카운터. 일시 장애와 차단을 **따로** 센다 — 대응이 다르다(백오프 vs 중지).

    `by_site`의 0은 지우지 않는다. "성공했는데 0건"이 구조 변경의 전형이기 때문이다(REL-06).
    """
    by_site = {o.site: o.deal_count for o in result.observations}
    return {
        "sites_polled": len(result.observations),
        "deals": sum(by_site.values()),
        "by_site": by_site,
        "failures": sum(1 for o in result.observations if o.outcome is Outcome.TRANSIENT),
        "blocked": sum(1 for o in result.observations if o.outcome is Outcome.BLOCKED),
        "alerts": len(result.alerts),
        "stopped_sites": sorted(name for name, state in result.states.items() if state.stopped),
    }
