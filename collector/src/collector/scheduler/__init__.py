"""폴링 스케줄 — 게시판당 1req/min 하한, 지수 백오프, robots 존중, 차단 시 자동 중지.

- `policy` — 순수 판정(레이트 하한·결과 3분해·백오프·사이트 상태 전이). IO 0.
- `loop`   — 사이트별 격리 1회 통과. `fetch`는 주입 포트.
- `sites`  — 핫딜 3사 레지스트리(URL·인코딩, docs/98 실측).
- `fetcher`— `Opener` 포트 뒤에 네트워크를 격리. robots 게이트 포함.

실 네트워크는 `fetcher.urllib_opener` 한 곳뿐이고, `__main__`이 `COLLECTOR_ALLOW_NETWORK=1`
없이는 그걸 부르지 않는다. DB 적재는 아직 없다(docs/91 Q-36).
"""
