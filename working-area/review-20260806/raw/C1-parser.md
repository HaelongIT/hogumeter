# C1 — collector 파서 + 파이프라인 리뷰

## 요약 (High 1 / Medium 1 / Low 0 / Info 1)
`pipeline/price.py`의 "원 없는 숫자" 폴백(`_BARE`)이 정규식 백트래킹으로 자신의 단위·문자 가드를 우회한다 — 5자리 이상 숫자 뒤에 문자·단위가 바로 붙으면(인텔 CPU 모델명 `14600K`류, `20000mAh`류 용량 표기) 태그 없는 거짓 가격이 조용히 만들어져 기준가를 오염시킬 수 있다(High). `parse_bunjang`은 같은 함수 안에서 일부 필드만 `.get()` 방어를 하고 `pid`·`update_time`은 직접 인덱싱해, API 응답 `list[]`의 항목 하나가 그 필드를 빠뜨리면 나머지 정상 항목까지 그 사이클 전체가 통째로 유실된다(Medium, 아직 프로덕션 미배선). 그 외 핵심 경로(가격·배송비 정규화, 타임스탬프, 뽐뿌 인코딩, 루리웹 종료 마커, 중고/신품 분리)는 실측 기반 테스트로 촘촘히 잠겨 있어 추가 결함을 찾지 못했다.

### C1-01 — `_BARE` 폴백이 정규식 백트래킹으로 자기 가드를 우회해 5자리+ 스펙·모델번호를 거짓 가격으로 읽는다 · High
- **위치**: `collector/src/collector/pipeline/price.py:55`
- **근거**:
  ```python
  _BARE = re.compile(rf"(?<![\d,])(?<![A-Za-z])(?<![A-Za-z]\s)(\d{{4,}})(?!\s*{_UNIT})(?![A-Za-z])")
  ```
  이 패턴은 주석(43~54줄)에서 스스로 "모델명은 가격이 아니다"라며 `RTX 5070`·`5600X`류를 막으려 만든 가드다. 그런데 `\d{4,}`는 **탐욕적**이라 뒤쪽 부정형 전방탐색(`(?!\s*{_UNIT})`, `(?![A-Za-z])`)이 실패하면 정규식 엔진이 자리수를 하나씩 줄여가며 재시도(백트래킹)한다. 숫자가 **정확히 4자리**일 때는 줄일 자리가 없어 가드가 통하지만(`5600X`→`None`, 골든 테스트 통과), 숫자가 **5자리 이상**이면 한 자리 줄인 4자리 부분 문자열의 뒤가 우연히 숫자(원래 5번째 자리)라서 두 부정형 전방탐색을 모두 통과해 버린다. 그 결과 원래 5자리 숫자의 **앞 4자리만 잘려 거짓 가격**이 된다. 접두 가드(`(?<![A-Za-z])`, `(?<![A-Za-z]\s)`)는 라틴 문자가 숫자 **바로 앞**에 있을 때만 막으므로 한글 뒤·공백 뒤에 오는 5자리+ 숫자는 이 우회에 그대로 노출된다.
- **실패 시나리오**: 실제로 재현했다(`uv run python`, `collector/src/collector`가 PYTHONPATH):
  | 제목 | 반환값 | 실제 |
  |---|---|---|
  | `i5-14600K CPU 특가` | `NormalizedPrice(headline_price=1460, applied_conditions=[])` | 가격 표기 없음(모델명) |
  | `인텔 13700K 정품 박스` | `headline_price=1370` | 가격 표기 없음 |
  | `인텔 14900K 리퍼브` | `headline_price=1490` | 가격 표기 없음 |
  | `보조배터리 20000mAh 대용량 충전기` | `headline_price=2000` | 가격 표기 없음(용량) |
  | `메모리 32768MB 서버용` | `headline_price=3276` | 가격 표기 없음(용량) |

  인텔 K시리즈(`14600K`·`13700K`·`14900K` 등)는 CPU 핫딜 게시글에 매우 흔한 표기이고, `mAh`/`MB` 뒤 5자리 숫자도 보조배터리·저장장치 핫딜에서 흔하다. 두 경우 모두 **태그 없이(`applied_conditions=[]`)** 1,000~3,300원대의 그럴듯한 가격이 만들어진다 — 하류(core BM-02)가 "말도 안 되게 싼 CPU/보조배터리 특가"로 읽어 기준가 표본을 오염시키거나 오알림을 낼 수 있다. 이는 절대 원칙 3("놓침 > 오알림")과 정면으로 반대되는 방향의 결함이다: 원래 이 파일이 막으려던 "거짓 가격은 놓침보다 나쁘다"(48~50줄 주석)는 취지가 5자리 이상 케이스에서 깨진다.
- **이미 막는 장치 확인**: `collector/tests/test_price.py::test_model_number_followed_by_a_letter_is_not_a_price`는 `5600X`(정확히 4자리)만 검증한다. `test_gpu_model_number_is_not_a_price`도 `RTX 5070`·`RTX 4090`(4자리, 게다가 접두 `RTX `로 이중 차단)만 본다. `docs/98-field-notes.md`의 "남은 위험은 `DDR5 5600`처럼 숫자 뒤 공백 뒤 숫자 — Q-65"는 **공백을 사이에 둔 4자리** 케이스만 인지하고 있고, 이번에 재현한 "5자리+ 숫자에 문자/단위가 바로 붙는" 백트래킹 우회는 Q-65에도, golden fixture 69건에도, 테스트 어디에도 없다(직접 grep·golden 전수 확인 — `14600`·`13700`·`14900`·`20000mAh`류 패턴 부재). 즉 **막혀 있지 않다.**
- **권고**: `\d{4,}`가 자기 가드를 우회하지 못하게 원자 그룹(atomic group)으로 백트래킹을 차단한다 — Python 3.12는 `(?>...)` 원자 그룹을 지원한다: `(?<![\d,])(?<![A-Za-z])(?<![A-Za-z]\s)(?>(\d{4,}))(?!\s*{_UNIT})(?![A-Za-z])`. 고친 뒤 `14600K`·`20000mAh`·`32768MB` 및 기존 golden 69건 전수(가격·태그 변화 0건이어야 함)를 회귀 테스트로 추가할 것.

### C1-02 — `parse_bunjang`이 일부 필드만 `.get()`으로 방어하고 `pid`·`update_time`은 직접 인덱싱해, 항목 하나의 결측이 그 사이클 전체를 삼킨다 · Medium
- **위치**: `collector/src/collector/parsers/bunjang.py:23`, `:34`
  ```python
  pid = str(item["pid"])
  ...
  posted_at=datetime.fromtimestamp(int(item["update_time"]), tz=timezone.utc),
  ```
  같은 함수 안에서 `price`·`status`·`num_faved`·`name`은 `item.get(...)`로 방어하는데(24·32·35·30줄), `pid`와 `update_time`만 방어 없이 직접 인덱싱한다.
- **실패 시나리오**: 재현했다(`uv run python`).
  ```
  list = [
    {pid:1, ..., update_time: 있음},   # 정상
    {pid:2, ..., update_time: 없음},   # API가 이 항목만 필드 하나를 누락
    {pid:3, ..., update_time: 있음},   # 정상
  ]
  → parse_bunjang(payload, now) 호출 시 KeyError: 'update_time'
  ```
  함수 안에 항목별 try/except가 없어 예외가 그대로 전파되고, `for item in data.get("list", [])` 루프 전체가 중단된다. 정상 항목 1·3까지 포함해 **그 사이클에서 얻은 모든 딜이 유실**된다. `scheduler/loop.py::_poll`이 이 예외를 잡아 `Outcome.TRANSIENT`로 흡수하므로 프로세스가 죽지는 않지만(REL-02 격리), 다른 3개 파서(뽐뿌·루리웹·펨코)는 bs4 `.select()`가 못 찾은 행을 그냥 `continue`로 건너뛰어 **정상 행은 살리는** 반면, bunjang은 한 항목의 결측이 배치 전체를 죽이는 비대칭 구조다.
- **이미 막는 장치 확인**: `collector/tests/test_parsers.py`·`test_ingest.py`의 번개 관련 테스트(`test_bunjang_golden_first_item`, `test_bunjang_free_shipping_adds_nothing_and_tags_nothing`, `_bunjang_payload` 헬퍼 등)는 전부 `pid`·`update_time`이 채워진 완전한 payload만 쓴다. 결측 필드를 넣는 테스트는 없다. `docs/98-field-notes.md`도 `status` 코드표 미실측(Q-44)은 기록했지만 `pid`/`update_time` 결측 가능성은 기록이 없다. `parse_bunjang`은 현재 `scheduler/sites.py`의 실 폴링 레지스트리(`hotdeal_boards()`)에 없어(M2 대기, `robots_check_targets()`에만 후보로 존재) **지금 당장 프로덕션 영향은 없다** — 그래서 High가 아니라 Medium으로 매긴다. 다만 `docs/98-field-notes.md`(2026-07-23)는 "번개는 파서·fixture가 있으니 마켓 폴링 루프를 지금 배선 가능"이라고 적어 배선이 임박했음을 시사한다.
- **권고**: `item.get("pid")`/`item.get("update_time")`도 나머지 필드처럼 방어적으로 읽고, 값이 없거나 타입이 안 맞는 개별 항목은 (파서 전체를 죽이지 않고) 건너뛰며 왜 건너뛰었는지 셀 수 있는 카운터를 두는 것을 권한다(다른 파서의 "조용히 스킵" 철학과 통일). 최소한 배선 전에 결측 필드 케이스를 fixture/합성 테스트로 추가할 것.

## 검토했으나 문제없음 (근거)
- **뽐뿌 EUC-KR/cp949 디코딩**: `parsers/ppomppu.py`는 디코딩된 `str`만 받고, 실제 디코딩은 `scheduler/fetcher.py::HttpFetcher.__call__`이 `errors="replace"` 없이 **strict**로 수행한다(주석: "제목이 조용히 깨진다" 방지). 디코딩 실패는 예외로 남고 `loop.py::_poll`이 `TRANSIENT`로 흡수한다 — "조용히 깨진 제목"이 DB에 들어가지 않는다. `test_parsers.py::_read_cp949`가 실제 cp949 바이트로 golden을 검증한다.
- **CSS 셀렉터 부재 시 예외 vs 침묵**: 3개 bs4 파서 전부 `soup.select()`/`select_one()`이 못 찾으면 조용히 빈 리스트·`None`을 반환하고(예외 없음), 이는 `docs/98`이 실측한 파서 실패 모드("조용한 0건")와 일치한다. `scheduler/drift.py`가 이를 감지하도록 설계돼 있고(조용한 0건 / 조용한 가격 0건 / 성공률 저하 3신호), REL-06 관련 테스트가 별도로 존재한다(직접 읽음, 이 리뷰 범위 밖 `scheduler/drift.py`이지만 인용된 계약이 실제로 존재함을 확인).
- **루리웹 `[종료]` 마커 — 제목 앵커 밖 텍스트 탐지**: `parsers/ruliweb.py::_has_end_marker`가 `title_anchor not in text.parents`로 앵커 안/밖을 정확히 가른다(직접 앵커의 자식뿐 아니라 임의 깊이의 자손까지 올바르게 처리 — `text.parents`는 전체 조상 체인). golden 28건 중 정확히 3건이 SOLD_OUT으로 잡히는지 `test_ruliweb_golden_has_three_sold_out_deals`가 고정하고, 제목 내부의 "종료"(`특가 종료 임박`)가 오탐되지 않는지도 `test_ruliweb_the_word_end_inside_the_title_is_not_a_sold_out_marker`가 검증한다.
- **배송비 "모름"을 0으로 사일런트 처리하지 않는가**: `pipeline/price.py::classify_shipping`의 마지막 분기가 항상 "해석 못 함"(`SHIPPING_UNKNOWN` + 설명 태그)으로 떨어지는 구조를 확인했다(`유배`, 펨코 조건부 무료, 번개 `free_shipping: false`, 픽업, 괄호 미지 토큰, 잘린 제목 전부 커버). `test_price.py`·`test_parsers.py`에 해당 케이스별 회귀 테스트가 각각 존재한다.
- **중고(번개)가 신품 기준가를 오염시키지 않는가**: `scripts/check-source-vocabulary.sh`가 `collector/src/collector/parsers/*.py` 각 파서를 core의 `NewProductSources` 허용집합 또는 `scripts/used-sources.txt` 중 정확히 한쪽에만 있도록 강제한다(스크립트 원문 확인). `parse_bunjang`이 신품 허용집합·중고 선언 양쪽에 동시에 있거나 어디에도 없으면 이 게이트가 FAIL한다 — 실제로 `scripts/used-sources.txt`를 열어보지는 못했지만 게이트 로직 자체는 두 사본 모순·미선언 둘 다 잡도록 짜여 있다.
- **`SHIPPING_UNKNOWN`/`FREE_PRICE` 등 값 계약이 정본-사본 드리프트를 일으키는가**: `scripts/check-tag-contract.sh`가 collector(`price.py`, 정본) vs core(`DealTags.java`) vs web(`present.ts`) 세 곳의 문자열 리터럴이 일치하는지 강제한다(주석만 걷어내고 코드 값만 비교하도록 짜여 있음을 원문으로 확인).
- **타임스탬프 tz-aware·연도 경계**: `pipeline/timestamps.py`의 `_DATE` 정규식은 `[/.]` 구분자만 허용해(하이픈 `MM-DD` 미허용) "연도 없는 MM-DD"가 매치될 여지 자체가 없다(매치 실패 시 `None` → `capturedAt` 폴백, 안전). 반환값은 전부 `tzinfo=KST`(고정 오프셋)라 tz-naive가 하류로 새는 경로를 찾지 못했다. `_today_at`의 자정 롤백(12시간 임계)은 `now`가 `__main__.py`에서 `datetime.now(timezone.utc)`로 항상 UTC-aware로 주입됨을 확인했다(서버 로컬 타임존에 의존하지 않음).
- **`ingest.py` 크기 상한·업서트 키**: `(site, post_id)` 자연키 업서트, SEC-05 상한(제목/URL/post_id/raw 바이트) 위반 시 "자르지 않고 버림 + 이유 기록"이 테스트로 고정돼 있다(`test_ingest.py` 전수 확인).

## 시간·범위 한계로 못 본 것
- `scheduler/fetcher.py`(HTTP 요청·robots 게이트)와 `scheduler/loop.py`/`scheduler/drift.py`/`scheduler/policy.py`는 리뷰 범위(파서+파이프라인) 밖이라 코드는 열람했지만 정밀 검토는 하지 않았다. `_poll`의 "예외를 통째로 TRANSIENT로 흡수"가 C1-02와 상호작용하는 지점만 확인했다.
- `scripts/used-sources.txt`의 실제 내용은 열어보지 않았다(게이트 로직만 확인) — `parse_bunjang`이 실제로 그 파일에 선언돼 있는지는 직접 대조하지 않았다.
- `_MANWON`의 소수+`천` 조합(`1.5만2천원` 류), `_SHIPPING_AMOUNT`가 비정상적으로 쉼표가 섞인 숫자(`1,2,3원`)를 만났을 때의 동작 등은 실측 golden에 사례가 없어 코드만으로 이론적 가능성을 확인했으나, 실 데이터 증거가 없어 별도 항목으로 올리지 않았다(추측 금지 원칙).
- `parse_bunjang`의 `price` 필드가 쉼표 포함 문자열(`"800,000"`)로 올 경우 `.isdigit()`이 `False`가 되어 "가격없음"으로 스킵되는 경로는 코드상 확인했으나, `docs/98`이 실측한 모든 사례가 쉼표 없는 순수 숫자 문자열이라 실제 발생 여부를 검증할 수 없어(추측 금지) 별도 발견으로 올리지 않았다.
- CI에서 이 정규식 백트래킹 우회(C1-01)에 대한 뮤테이션 테스트나 fuzzing은 수행하지 않았다 — 손으로 고른 몇 개 사례(CPU 모델명 3종, 용량 표기 2종)만 재현했다. 5자리 이상 숫자+단위/문자 조합의 전체 공간을 체계적으로 훑지는 못했다.
