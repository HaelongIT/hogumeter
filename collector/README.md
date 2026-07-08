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
├── pipeline/        # 정규화(가격 추출)·적재
│   └── price.py     # 실결제가+배송비 규칙 기반 파싱 (as-posted, 카드 역산 금지)
├── scheduler/       # 폴링 루프 (게시판당 1req/min, 백오프)
└── __main__.py
tests/
├── fixtures/{ppomppu,ruliweb,fmkorea,bunjang}/   # golden HTML/JSON
├── test_parsers.py / test_price.py / test_smoke.py
```

## 원칙
- **파서는 네트워크와 완전 분리**: fetch는 scheduler, 파싱은 parsers. 파서 테스트는 fixture만.
- **멱등**: `raw_deal_post`는 `(site, post_id)` UNIQUE로 insert-only. 같은 글 두 번 넣어도 결과 불변.
- **플랫폼 잣대**(원칙5): 공식 API 우선 / 기술적 차단 우회 금지 / 저빈도·개인용. 사이트 구조 변경은 파싱 성공률 하락으로 감지 → 관리 알림.
- 사이트별 실측(셀렉터·차단 징후·fixture 채취일)은 [`../docs/98-field-notes.md`](../docs/98-field-notes.md)에 기록.

## DB 계약 (core ↔ collector)
- `raw_deal_post`: collector insert-only. core가 소비 후 매칭·병합.
- `used_listing_observation`: 번개 폴링 관측 insert-only. core가 생애주기 판정.
- 스키마 진화는 **Flyway(core) 단독 소유** — collector는 마이그레이션 금지, 계약 테이블만 접근.
