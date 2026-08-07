# D1 — 모듈 간 계약 드리프트 리뷰
## 요약 (High 0 / Medium 3 / Low 2 / Info 2)
core↔collector DB 계약 자체(컬럼 이름·타입·null 허용·enum 허용집합)는 대조표 기준으로 **실질 불일치 없음** — `raw_deal_post`·`used_listing_observation`·`site_poll_state`·`used_search`·`alias_dictionary` 전부 정합했다. 대신 두 갈래 드리프트를 찾았다: ① `check-dead-columns.sh`가 "이름이 나타난다"만 보고 "core가 실제로 읽는다"를 못 구별해 **`raw_deal_post.reaction_score`가 collector→DB로는 살아있는데 core 소비처가 0인 채로 GREEN을 낸다**(docs/90 확정본이 명시한 필드인데 미배선) — 이게 가장 심각. ② `docs/91` Q-3의 재개 트리거("네이버 키 발급 시")가 Q-3 본문·D-7에서 이미 죽은 것으로 확인됐는데도 `price_history` 관련 allowlist 2곳이 옛 사유를 그대로 인용한다(게이트는 Q-ID open 여부만 보고 트리거 내용은 안 봐서 통과). 나머지는 문서 사소 드리프트(Low)다.

### D1-01 — `raw_deal_post.reaction_score`가 collector→DB로는 살아있지만 core 소비처 0, 게이트는 "배선됨"으로 오판 · Medium
- **위치**: `collector/src/collector/db/raw_deal_sink.py:29-44`(INSERT·ON CONFLICT UPDATE에 `reaction_score` 포함) ↔ `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPost.java`(엔티티에 `reactionScore` 필드 없음 — id·site·postId·url·title·headlinePrice·postedAt·capturedAt·status·origin만 매핑) + `V1__init.sql:52`(`reaction_score numeric`)
- **근거**: collector는 매 폴링마다 `reaction_score`(추천수)를 써서 갱신한다(`_UPSERT`의 `reaction_score = excluded.reaction_score`). core 쪽엔 이 컬럼을 읽는 코드가 전혀 없다 — `grep -rni reaction core/src web/src`로 전수 확인, 유일한 매치는 `RawDealPost.java:13`의 javadoc 주석(코드 아님, D1-03 참조)뿐이고 실제 필드·게터·네이티브 SQL·`DealEvent` 도메인 어디에도 없다. 그런데 `docs/90-planning-final.md:58,187`(기획 확정본, 최종 권위)은 `DealEvent`가 `reaction_score`(댓글수·추천수 등 반응 신호)를 필드로 노출해야 한다고 명시한다 — "원문 링크(source_url)는 1급 데이터로 항상 보존·노출. 반응 신호(reaction_score: 댓글수·추천수 등)를 가능한 만큼 수집."
- **게이트 오판 재현**: `bash scripts/check-dead-columns.sh`를 직접 실행 → `DEAD COLUMNS OK: 컬럼 180개 (면제 6개, 나머지 배선됨)` — `reaction_score`는 면제 목록(`scripts/dead-columns-allowlist.txt`)에 없는데도 FAIL 없이 통과한다. 원인은 게이트의 `reached()`가 "컬럼 이름이 **core/collector/web 셋 중 아무 프로덕션 코드**에 나타나는가"만 보기 때문 — `collector/src/collector/db/raw_deal_sink.py`가 `reaction_score`를 **쓰는** 코드에 그 이름을 갖고 있으므로 "배선됨"으로 판정된다. 게이트 설계가 "생산자가 이름을 안다"와 "소비자가 실제로 읽는다"를 구별하지 못한다 — 이 프로젝트 원칙("정적 검사에서 '이름이 나타난다'와 '실행된다'를 구별하라")이 정확히 지목하는 패턴이 그대로 재현됐다.
- **실패 시나리오**: collector가 추천수를 계속 수집해 DB에 정직하게 쌓지만, core는 그 값을 한 번도 읽지 않으므로 사용자는 화면에서 "반응 신호"를 영원히 볼 수 없다. 데이터가 유실되지는 않지만(DB엔 남아있다) 확정본이 요구한 기능이 조용히 빠진 채로 아무 게이트도, 아무 열린 Q도 이 사실을 추적하지 않는다 — `docs/91`을 전수 grep해도 `reaction_score` 노출 미착수를 다루는 항목이 없다.
- **이미 막는 장치 확인**: `scripts/check-dead-columns.sh`가 있지만 위에서 실행 확인한 대로 **이 케이스를 못 잡는다**(false negative). `scripts/domain-consumers-allowlist.txt`·`repository-readers-allowlist.txt`는 각각 "순수 도메인 클래스"·"리포지토리 조회 메서드" 스코프라 컬럼 단위 미소비는 범위 밖이다.
- **권고**: (a) `docs/91`에 새 Q를 만들어 "reaction_score 미노출"을 명시적으로 열어 두거나, (b) 확정본 요구를 포기하기로 했다면 `decision-log.md`에 그 결정을 기록하고 `docs/90`에 델타를 남긴다. 게이트 쪽은 `check-dead-columns.sh`의 `reached()`를 모듈별로 분리해(collector 쓰기 vs core 읽기) "collector만 안다 = 절반만 배선"을 구별하게 하는 것이 근본 수정이나, 이번 리뷰 범위(정적 대조)를 넘는 리팩터라 별도 작업으로 제안만 남긴다.

### D1-02 — `price_history` allowlist 2곳이 Q-3의 죽은 재개 트리거("키 발급 시")를 그대로 인용 · Medium
- **위치**: `scripts/dead-columns-allowlist.txt`(`price_history.fetched_at  Q-3  네이버 어댑터(키 미발급)가 쓸 죽은 테이블. 키 발급 시 배선.`) + `scripts/table-wiring-allowlist.txt`(`price_history  Q-3  네이버 쇼핑 API 키 미발급 - 현재가 수집기가 없다`) ↔ `docs/91-open-questions.md:22-28`(Q-3 본문) + `working-area/decisions-needed.md:28-40`(D-7)
- **근거**: 두 allowlist 항목은 재개 조건을 "키 발급"으로 서술한다. 그런데 Q-3 본문(2026-07-24 갱신)이 명시적으로: "네이버 개발자센터가... **2026-07-31(금)**부로 전면 종료한다고 공지... **키를 지금 발급받아도 일주일 뒤면 죽는다** — '재개 트리거 = 키 발급'이라는 이전 잠정값이 통째로 무의미해졌다... 재개 트리거를 새로 정하지 않는다 — 사람이 정할 사안으로 승격." 그리고 D-7이 한 번 더 못박는다: "'키 발급 대기'는 더 이상 유효한 계획이 아니다." 두 allowlist 줄의 사유("키 발급 시 배선")는 프로젝트 자신의 최신 판단과 정면으로 모순된다.
- **실패 시나리오**: 다음 세션(또는 나)이 allowlist만 보고 "네이버 키만 받으면 이 면제가 풀리겠구나"로 오판해 실제로 키를 발급받아도, 서비스가 이미 종료돼(오늘 기준 2026-08-07, 종료일 2026-07-31 지남) 아무것도 안 풀리는 헛수고를 하게 된다. 진짜 다음 행동(대체 데이터 소스 결정, D-7)이 allowlist에서는 안 보인다.
- **이미 막는 장치 확인**: `scripts/check-dead-columns.sh`·`scripts/check-table-wiring.sh`는 인용된 Q-ID가 `docs/91`에서 `[열림]`인지만 본다(스크립트 원문 확인: `board="$root/docs/91-open-questions.md"` 로드 후 "Q가 열려 있는지"만 판정, 트리거 문구는 안 읽는다). Q-3는 여전히 `[열림]`이므로 두 게이트 모두 통과한다(직접 실행 확인: `DEAD COLUMNS OK`·`TABLE WIRING OK`). `scripts/check-board-references.sh`도 자체 주석에 "**한 방향만 본다**: 보드에 있는데 아무도 인용하지 않는 Q는 정상이다"라고 명시 — 보드가 갱신됐는데 인용측 사유가 안 갱신된 경우는 대상이 아니다. 즉 세 게이트 모두 이 드리프트를 구조적으로 못 잡는다(명시된 한계 그대로).
- **권고**: 두 allowlist 줄의 사유를 "네이버 쇼핑 API 2026-07-31 서비스 종료(대체 안 됨) — 재개는 D-7 확정 후"로 갱신한다. Q-ID는 그대로 Q-3 유지(여전히 열려 있고 인용도 유효 — `check-board-references.sh` 관점에서 문제 없음), 사유 문구만 최신화.

### D1-03 — `RawDealPost.java` 클래스 javadoc이 실제로 매핑된 컬럼을 "미매핑"이라 오기 · Low
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPost.java:13`("nullable 컬럼(body_text·headline_price·posted_at·reaction_score·raw jsonb)은 이 슬라이스에서 미매핑") ↔ 같은 파일 36-40행(`@Column(name="headline_price") private Long headlinePrice;`, `@Column(name="posted_at") private Instant postedAt;`)
- **근거**: javadoc은 `headline_price`·`posted_at`도 미매핑이라 적었지만 바로 아래 필드 선언에서 둘 다 `@Column`으로 매핑돼 있고 `getHeadlinePrice()`·`getPostedAt()`가 실제로 도메인 로직(`IngestDealsUseCase.ingestOne`의 `post.getHeadlinePrice() == null` 널 가드, `candidateFrom`의 `firstSeen` 계산)에 쓰인다. 진짜 미매핑은 `body_text`·`reaction_score`·`raw` 셋뿐이다.
- **실패 시나리오**: 코드 자체 동작엔 영향 없다(문서만 틀림). 다만 이 주석을 믿고 "headline_price/posted_at은 core가 못 읽으니 네이티브 SQL로 다뤄야 한다"고 오판하거나, 반대로 진짜 미매핑인 `reaction_score`가 매핑된 두 컬럼과 한 목록에 섞여 있어 "이것도 곧 매핑되겠거니" 하고 D1-01의 실제 미배선 상태를 놓치기 쉽다.
- **이미 막는 장치 확인**: 없음 — 주석 정확성을 검사하는 게이트는 없다(설계상 당연히 범위 밖).
- **권고**: javadoc을 "미매핑: body_text·reaction_score·raw jsonb"로 정정.

### D1-04 — `used_listing_observation.raw`도 같은 게이트 사각지대를 통과 · Info
- **위치**: `collector/src/collector/db/used_listing_sink.py:69`(`"raw": Jsonb(deal.raw) if deal.raw else None`) ↔ `core/src/main/java/dev/hogumeter/core/adapter/persistence/UsedListingObservationEntity.java:16`("core는 읽기만 한다 — 그래서 raw(jsonb 크롤링 원본)는 매핑하지 않는다")
- **근거**: 엔티티 자체가 "raw는 core가 안 읽는다"고 설계 의도로 명시하므로 D1-01과 달리 **버그가 아니라 의도**로 보인다(raw_deal_post.raw와 같은 "크롤링 원본 보관 전용" 패턴). 다만 `scripts/dead-columns-allowlist.txt`에 `used_listing_observation.raw`가 선언돼 있지 않다 — `check-dead-columns.sh`가 이 컬럼도 (collector가 쓰는 코드 안에 "raw"라는 이름이 나타나므로) "배선됨"으로 판정해 통과시키는 것으로 추정된다(D1-01과 동일 메커니즘). 실제로 FAIL 없이 게이트가 통과한다는 것은 확인했다(`DEAD COLUMNS OK`, 6개 면제 외 전부 배선 판정).
- **실패 시나리오**: 지금 당장은 문제 없음(의도된 설계). 다만 나중에 이 컬럼이 정말 죽었는지/설계인지 판단할 근거가 코드 주석 하나뿐이라, `dead-columns-allowlist.txt`의 INTENTIONAL 패턴(`deal_event.base_price` 등)처럼 명시적으로 선언해 두지 않으면 다음 감사가 "게이트가 통과시켰으니 배선됐다"고 잘못 믿을 위험이 있다.
- **이미 막는 장치 확인**: 없음(게이트가 우연히 통과시킴, 강제 아님).
- **권고**: `used_listing_observation.raw  INTENTIONAL  크롤링 원본 보관 전용, core는 읽기만 하는 테이블이라 설계상 미매핑`을 `dead-columns-allowlist.txt`에 추가해 "우연히 통과"를 "선언적으로 면제"로 바꾼다. 급하지 않음.

### D1-05 — `repository-readers-allowlist.txt`의 안내 주석이 현재 파일 상태와 안 맞음 · Low
- **위치**: `scripts/repository-readers-allowlist.txt`(마지막 줄: "테스트는 호출자가 아니다. 두 메서드 다 테스트에서만 불린다.") ↔ 파일 본문에 실제 데이터 행 0개
- **근거**: 주석은 "두 메서드"가 면제 대상인 것처럼 말하지만 파일에 실제 `<Repository>.<method>` 행이 하나도 없다. `bash scripts/check-repository-readers.sh` 직접 실행 결과: `REPOSITORY READERS OK: 조회 메서드 36개 (호출됨 36 · 미사용 선언 0)` — 즉 현재는 미사용 메서드가 0개이고 예전에 있었던 2개가 이미 배선되며 행이 지워졌는데, 그 사실을 알리는 마지막 문장만 안 지워진 것으로 보인다.
- **실패 시나리오**: 게이트 동작엔 영향 없음(주석은 파싱 대상이 아니다 — `while read -r table qid _rest; do case "$table" in '' | '#'*) continue ;; esac`로 주석 줄은 스킵). 사람이 이 파일을 훑을 때만 혼란(있지도 않은 "두 메서드"를 찾게 됨).
- **이미 막는 장치 확인**: 없음(주석 정확성은 게이트 범위 밖).
- **권고**: 마지막 줄을 지우거나 "현재 미사용 선언 0건"으로 갱신.

## core ↔ collector 컬럼 계약 대조표

| 테이블.컬럼 | collector 쓰기 | core 읽기 | DDL 타입/제약 | 일치 |
|---|---|---|---|---|
| raw_deal_post.site | `raw_deal_sink.py:66` | `RawDealPost.java:25` (`site`, not null) | `text not null` (V1) | ✅ |
| raw_deal_post.post_id | `raw_deal_sink.py:67` | `RawDealPost.java:28` (`postId`) | `text not null`, UNIQUE(site,post_id) | ✅ |
| raw_deal_post.url | `raw_deal_sink.py:68` | `RawDealPost.java:31` | `text not null` | ✅ |
| raw_deal_post.title | `raw_deal_sink.py:69` | `RawDealPost.java:34` | `text not null` | ✅ |
| raw_deal_post.body_text | 안 씀(collector가 목록만 폴링, Q-18) | 안 읽음(미매핑, javadoc 참조) | `text` nullable | ✅ 양쪽 다 죽음 — `dead-columns-allowlist.txt`에 Q-18로 선언됨 |
| raw_deal_post.headline_price | `raw_deal_sink.py:70` (`int|None`) | `RawDealPost.java:37`(`Long headlinePrice`), 널 가드는 `IngestDealsUseCase.java:107` | `bigint` nullable | ✅ |
| raw_deal_post.posted_at | `raw_deal_sink.py:71`, UPSERT는 `COALESCE(기존,신규)`로 불변+후채움 | `RawDealPost.java:40`(`Instant postedAt`) | `timestamptz` nullable | ✅ |
| raw_deal_post.captured_at | `raw_deal_sink.py:72`(항상 값 있음) | `RawDealPost.java:43`(not null) | `timestamptz not null` | ✅ |
| raw_deal_post.reaction_score | `raw_deal_sink.py:73`, UPSERT마다 갱신 | **없음** — 미매핑, 네이티브 SQL도 없음 | `numeric` nullable | ❌ **D1-01** — 게이트가 놓친 미소비 |
| raw_deal_post.status | `raw_deal_sink.py:74`, `ingest.py:16` `_VALID_STATUS={ACTIVE,SOLD_OUT,DELETED}`로 선검증 | `RawDealPost.java:46` → `DealStatus.fromRawPostStatus`(`ENDED_RAW_STATUSES={SOLD_OUT,DELETED}`) | `text not null check in (ACTIVE,SOLD_OUT,DELETED)` | ✅ 허용집합 3종 일치, core의 2종 축약(ACTIVE/ENDED)은 의도된 설계 |
| raw_deal_post.raw | `raw_deal_sink.py:75`(jsonb, `_derived.applied_conditions` 포함) | 미매핑이나 **네이티브 SQL로 읽음**(`PreserveAppliedConditionsUseCase`의 `post.raw -> '_derived' -> 'applied_conditions'`) | `jsonb` | ✅ (엔티티 미매핑이지만 SQL 경로로 실제 소비됨 — 죽지 않음) |
| raw_deal_post.origin | `ingest.py:78` 기본값 `"LIVE"`(백필 collector 없음) | `RawDealPost.java:50`, `Origin.valueOf(...)`로 `deal_event.origin`에 그대로 전파(`IngestDealsUseCase.java:255`) | `text not null check in (LIVE,BACKFILL)` (V23) | ✅ |
| used_listing_observation.used_search_id | `used_listing_sink.py:62` | `UsedListingObservationEntity.java:28` | `bigint not null references used_search` | ✅ |
| used_listing_observation.listing_id | `used_listing_sink.py:63` | `UsedListingObservationEntity.java:31` | `text not null` | ✅ |
| used_listing_observation.title | `used_listing_sink.py:64` | `UsedListingObservationEntity.java:34` | `text not null` | ✅ |
| used_listing_observation.price | `used_listing_sink.py:65`, `price is not None` 필터 후 삽입 | `UsedListingObservationEntity.java:37`(`long`, not null) | `bigint not null` | ✅ |
| used_listing_observation.observed_at | `used_listing_sink.py:70`(주입값) | `UsedListingObservationEntity.java:40` | `timestamptz not null` | ✅ |
| used_listing_observation.url | `used_listing_sink.py:67`(파서가 조립, core는 재조립 안 함) | `UsedListingObservationEntity.java:44`(nullable, V13 이전 관측 대비) | `text` nullable (V13) | ✅ |
| used_listing_observation.raw | `used_listing_sink.py:69` | 의도적으로 미읽음(설계) | `jsonb` nullable | ⚠️ **D1-04** — 의도이나 allowlist 미선언 |
| site_poll_state.site | `site_poll_state_sink.py:74` | `SitePollStateEntity.java:24`(PK) | `text primary key` | ✅ (`{platform}#{search_id}` 네임스페이스 포함) |
| site_poll_state.last_successful_poll_at | `site_poll_state_sink.py:75`, 단조증가만 허용 | `SitePollStateEntity.java:27` | `timestamptz` nullable(V15) | ✅ |
| site_poll_state.consecutive_failures / next_attempt_at / stopped | `site_poll_state_sink.py:76-78` | 의도적으로 미매핑(D-3: 재개는 운영자 수동 UPDATE) | V15 컬럼들 | ✅ 문서화된 설계 |
| used_search.id/platform/required_keywords/poll_interval_min | 읽기 전용(`used_search_source.py:16-19`) | 쓰기 주체(core REST) | V3 | ✅ |
| used_search.exclude_keywords/target_price | collector가 **의도적으로 안 읽음**(주석: "거르는 판단은 core가 읽을 때 한다") | core 소유 | V3 | ✅ 설계 근거 명시 |
| alias_dictionary.alias | 읽기 전용(`alias_source.py:14`) | 쓰기 주체(core 등록 REST) | V1 | ✅ |

## allowlist 만료 조건 점검

| allowlist 파일 | 항목 수 | 만료 조건(Q-ID) 있음 | Q 아직 열림 | 문제 |
|---|---|---|---|---|
| dead-columns-allowlist.txt | 6 (INTENTIONAL 3 + Q인용 3) | INTENTIONAL 3건은 만료 없음(설계상 영구, 근거 명시됨) / Q인용 3건 모두 있음 | Q-68✅ Q-3✅(형식상) Q-18✅ | **Q-3 항목의 사유 문구가 Q-3 본문·D-7과 모순**(D1-02) — 형식(Q-ID open)은 통과하나 내용이 낡음 |
| domain-consumers-allowlist.txt | 0(활성 행 없음, 과거 항목은 주석 처리돼 "해소됨"으로 남음) | 해당 없음 | 해당 없음 | 없음 |
| network-optin-allowlist.txt | 4 | Q-ID 형식 아님(파일명+사유) — 이 게이트는 애초에 Q-ID를 요구하지 않음(설계: "URL이 실행되지 않는다"는 자기완결 사실) | 해당 없음 | 없음 — 설계 의도대로 |
| repository-readers-allowlist.txt | 0(활성 행 없음) | 해당 없음 | 해당 없음 | 트레일링 주석이 과거 상태를 언급(D1-05), 게이트엔 무해 |
| table-wiring-allowlist.txt | 1 (`price_history` → Q-3) | 있음 | Q-3✅(형식상) | D1-02와 동일한 사유 — "키 미발급"이 Q-3 본문과 모순 |

`check-board-references.sh`의 반대 방향 검사 여부: **검사하지 않는다.** 스크립트 자신의 주석이 "한 방향만 본다: 보드에 있는데 아무도 인용하지 않는 Q는 정상이다"라고 명시하며, "닫힌 Q를 코드가 여전히 인용" 케이스는 애초에 이 게이트의 판정 대상이 아니다(Q가 닫혀도 제목만 지워지고 본문에 "(Q-38 해소됨 … 여기서 제거)" 식으로 문자열이 남는 설계라 "인용된 Q가 보드에 있다"는 항상 참이 된다 — 닫힘 여부와 무관). 실제로 이번 리뷰에서 발견한 D1-02는 "Q가 열려는 있으나 그 인용의 근거 문구가 갱신 안 됨" 케이스라 이 게이트의 판정 범위 밖이며, 스크립트 자신이 이 한계를 정직하게 적어 두고 있다.

## 검토했으나 문제없음 (근거)
- `raw_deal_post` 상태 허용집합: collector `_VALID_STATUS`(`ingest.py:16`) = DB CHECK(`V1__init.sql:54`) = 정확히 `{ACTIVE, SOLD_OUT, DELETED}`. core의 `DealStatus.ENDED_RAW_STATUSES`(`DealStatus.java:38`)는 이 중 종료 2종만 참조 — 서브셋 축약이지 불일치가 아니다(주석에 "정본"으로 명시).
- `origin` 허용집합: `raw_deal_post.origin`(V23)과 `deal_event.origin`(V1)의 CHECK가 둘 다 `('LIVE','BACKFILL')`로 동일, core `Origin` enum(`Origin.java`)도 정확히 이 두 값. `Origin.valueOf(post.getOrigin())`이 예외 없이 안전하게 왕복.
- `SHIPPING_UNKNOWN`("배송비미상")·`FREE_PRICE`("무료가") 문자열 표식: `scripts/check-tag-contract.sh` 직접 실행 → `TAG CONTRACT OK` GREEN. 세 사본(collector `price.py`/core `DealTags.java`/web `present.ts`)이 실제로 일치.
- `PAID_SHIPPING_UNKNOWN`("유료배송(금액미상)")·`f"조건부무료배송:{...}"`·`f"수령:{...}"`·`f"배송비:{...}"` 등은 게이트가 검사하지 않지만, `price.py` 자체 주석이 이들을 "사람이 읽는 설명 태그"로 명시하고 소비처(`DealTags`·web)는 기계 판정에 `SHIPPING_UNKNOWN`/`FREE_PRICE`만 쓴다 — "한 필드가 사람용 설명과 기계용 분류를 겸하지 않는다" 원칙이 실제로 지켜지고 있어 사본 누락이 아니라 설계.
- `scripts/check-source-vocabulary.sh` 직접 실행 → `SOURCE VOCABULARY OK: 파서 4개 — 신품 3 / 중고선언 1`. `NewProductSources`(core)·`used-sources.txt`가 collector 파서 4개(뽐뿌·루리웹·펨코·번개) 전부를 모순 없이 분류.
- `RawDealPostUpserter`(core)는 프로덕션 호출자가 0(테스트에서만 사용, `grep -rln`으로 확인) — 문제가 아니라 `raw_deal_sink.py`의 javadoc이 스스로 "이건 명세일 뿐 쓰기 주체가 아니다, 프로덕션에서 아무도 호출 안 한다"고 정확히 문서화한 그대로다.
- SEC-05 크기 상한(`MAX_TITLE`=300·`MAX_URL`=2000·`MAX_POST_ID`=64·`MAX_RAW_BYTES`=256KiB, `ingest.py:23-26`)은 DB `text`/`jsonb`가 무제한이라 컬럼 타입과 "불일치"처럼 보이지만, collector가 유일한 쓰기 주체이고 상한을 넘기면 **적재 자체를 안 하므로**(`to_raw_records`의 `_first_violation` 필터) DB 쪽 제약과 모순될 데이터가 애초에 안 만들어진다 — 단방향 자기 규율이지 계약 드리프트가 아니다.
- `used_search`의 `exclude_keywords`·`target_price`를 collector가 읽지 않는 것은 `used_search_source.py:58-63`의 주석("거르는 판단은 core가 읽을 때 한다 — 절대 원칙 4")이 근거를 명시한 의도된 설계.
- migrations 실행 순서: `collector/tests/conftest.py:17`이 `core/src/main/resources/db/migration`을 직접 가리켜 **실제 Flyway SQL을 그대로** 테스트 컨테이너에 적용한다(정적 미러 사본이 아니다) — "미러가 무엇을 덮는가" 질문에 대한 답은 "전부, 왜냐면 사본이 아니라 원본을 그대로 쓰기 때문"이다. `test_schema_fixture.py`는 버전 정렬(V10 vs V2)만 별도로 검증한다.

## 시간·범위 한계로 못 본 것
- `detail_fetch.py`(루리웹 상세 fetch)·`alias_source.py`가 소비하는 `alias_dictionary`의 전역/제품별 분기 로직은 core REST 등록 경로까지 왕복 대조하지 않았다(읽기 전용 확인만 함).
- `docs/benchmark/04`·`docs/used/04`(인수조건) 전체 대 테스트 코드 1:1 대조는 표본만 확인했다(기준가 산식·상태 전이·에러코드 위주) — 전 조항 대조는 못 했다.
- `.claude/hooks/guard.sh`·CI 워크플로 전체 단계 순서(어떤 게이트가 어떤 잡에서 도는지)는 `ci.yml`에서 각 스크립트 이름의 존재만 grep으로 확인했고, job 간 의존성·병렬 실행 순서까지는 안 봤다.
- `web/src/api/types.ts` ↔ core 응답 record 대조는 지시대로 범위에서 제외(C3 담당).
- `dead_columns` 게이트의 나머지 174개 컬럼(면제 6개 제외) 전부를 하나하나 "진짜 core가 읽는지" 수동 재검증하지는 않았다 — `reaction_score`·`used_listing_observation.raw` 두 건은 D1-01/D1-04로 구체 확인했지만, 같은 패턴(생산자만 아는 이름)이 다른 컬럼에도 더 있을 가능성은 배제 못 한다.
