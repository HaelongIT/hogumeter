# collector — 핫딜/중고 수집기 (Python)

핫딜 3사(뽐뿌·루리웹·펨코)와 번개장터를 폴링·파싱해 PostgreSQL에 적재하는 별도 컨테이너. krepe90/user-hotdeal-bot 골격을 최대 재활용한다.

- 스택: **Python 3.12 · uv · pytest · BeautifulSoup**
- core와는 **DB 테이블 계약으로만** 접합(직접 호출 없음). collector는 무상태(마지막 폴링 커서 제외).

## 테스트

```bash
uv run pytest       # 파서 golden + 파이프라인
```

- 파서 테스트는 **fixture HTML/JSON만** 사용(실 네트워크 호출 금지). fixture는 `tests/fixtures/{site}/`.

## 구조

```
src/collector/
├── parsers/         # 사이트별 파서. 순수 함수: html/json → DTO (네트워크 분리)
│   ├── ruliweb.py / fmkorea.py / bunjang.py   # 뽐뿌는 재채취 대기(docs/91 Q-5)
│   └── models.py    # DTO
├── pipeline/        # 정규화(가격 추출)·적재 준비
│   ├── price.py     # 실결제가+배송비 규칙 기반 파싱 (as-posted, 카드 역산 금지)
│   └── ingest.py    # ParsedDeal → raw_deal_post 레코드 + 배치 멱등(자연키 dedup)
├── scheduler/       # 폴링 루프 (게시판당 1req/min 하한, 백오프, 차단 시 중지)
│   ├── policy.py    # 순수: 레이트 하한·결과 3분해·지수 백오프·사이트 상태 전이
│   └── loop.py      # 사이트별 격리 1회 통과. fetch는 주입 포트(실 구현 없음)
└── __main__.py      # 스텁 — 실 fetch가 없어 아직 루프를 돌리지 않는다
tests/
├── fixtures/{ppomppu,ruliweb,fmkorea,bunjang}/   # golden HTML/JSON
├── test_parsers.py / test_price.py / test_ingest.py / test_scheduler.py / test_smoke.py
```

## 미구현 (정직하게)
- **실 HTTP fetch·DB 적재**: 스케줄러는 `fetch` 포트와 상태 커서까지만. 실 네트워크·신규 의존(psycopg 등)은 승인 대상 — `docs/91` Q-36.
- **robots.txt 게이트**(SEC-08): 미구현. 현재 크롤링 윤리는 코드 강제 레이트 하한 + 차단 신호(403/429) 즉시 중지로만 이행 — Q-38.
- **파싱 드리프트 감지**(REL-06): 파싱 실패를 일시 장애로 흡수만 한다 — Q-40.
- **뽐뿌 파서**: fixture 재채취 대기 — Q-5.

## 원칙
- **파서는 네트워크와 완전 분리**: fetch는 scheduler, 파싱은 parsers. 파서 테스트는 fixture만.
- **멱등**: `raw_deal_post`는 `(site, post_id)` UNIQUE로 insert-only. 같은 글 두 번 넣어도 결과 불변.
- **플랫폼 잣대**(원칙5): 공식 API 우선 / 기술적 차단 우회 금지 / 저빈도·개인용. 사이트 구조 변경은 파싱 성공률 하락으로 감지 → 관리 알림.
- 사이트별 실측(셀렉터·차단 징후·fixture 채취일)은 [`../docs/98-field-notes.md`](../docs/98-field-notes.md)에 기록.

## DB 계약 (core ↔ collector)
- `raw_deal_post`: collector insert-only. core가 소비 후 매칭·병합.
- `used_listing_observation`: 번개 폴링 관측 insert-only. core가 생애주기 판정.
- 스키마 진화는 **Flyway(core) 단독 소유** — collector는 마이그레이션 금지, 계약 테이블만 접근.
