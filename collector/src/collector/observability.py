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

from .pipeline.price import SHIPPING_UNKNOWN
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

    `conditional`은 조건부 가격(`카할`=카드할인 · `유료배송(금액미상)` · 펨코 `조건부무료배송:…`)
    딜 수다. 그 태그는 `raw._derived`까지만 가고 **`deal_event`에 도달하지 않는다**(docs/91 Q-46) —
    즉 그만큼의 딜이 **무조건 가격인 척** 기준가 표본에 들어간다. 우리가 못 고치는 결함이라면
    **세어서 노출한다**: 폴링을 켠 사람이 `docker logs`에서 오염률을 바로 본다
    (골든 실측: 뽐뿌 9.5% · 펨코 15%). 0도 센다 — "조건부 0건"과 "안 셌다"는 다른 사건이다.
    """
    by_site = {o.site: o.deal_count for o in result.observations}
    return {
        "sites_polled": len(result.observations),
        "deals": sum(by_site.values()),
        "by_site": by_site,
        "failures": sum(1 for o in result.observations if o.outcome is Outcome.TRANSIENT),
        "blocked": sum(1 for o in result.observations if o.outcome is Outcome.BLOCKED),
        "alerts": len(result.alerts),
        # BM-02 AC-3: 가격 패턴이 없으면 딜이 되지 못한다(미상 버킷과 다르다 — 그건 제품 축 판별 실패다).
        # 세지 않으면 "표본이 왜 안 쌓이지"에 답할 수 없다. golden 실측: 루리웹 28딜 중 10건(36%).
        "no_price": sum(1 for deal in result.deals if deal.headline_price is None),
        "conditional": sum(1 for deal in result.deals if deal.applied_conditions),
        # `conditional`의 부분집합. 배송비를 모른 채 0을 더한 딜 — 저장된 가격은 실결제가가
        # 아니라 **하한**이고 기준가를 아래로 끈다(BM-02, docs/91 Q-46). 폴링을 켠 사람이
        # `docker logs`에서 이 비율을 바로 본다. 0도 센다(OBS-02).
        "shipping_unknown": sum(
            1 for deal in result.deals if SHIPPING_UNKNOWN in deal.applied_conditions
        ),
        "stopped_sites": sorted(name for name, state in result.states.items() if state.stopped),
    }
