# 코드리뷰 20260806 — 10. 백엔드 (core · collector)

> 리뷰어 8명(A1·A2·A3·B1·B2·B3·C1·C2)의 발견을 통합. 원시 산출물은 `raw/`, 반박 검증은 `rebuttal/`.
> **1차 = 정적 검증**이며 범위는 발견까지다 — 수정은 2차.

## High

### BE-01 — 딜 병합 시 도메인이 계산한 수요축 값이 엔티티에 반영되지 않는다 · High · deal-merge · ✅수정완료(0a9b9bb)
- **위치**: `core/src/main/java/dev/hogumeter/core/application/IngestDealsUseCase.java:154-156`, `core/src/main/java/dev/hogumeter/core/domain/deal/DealMergePolicy.java:69,78-86`, `core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventEntity.java:195-208`
- **근거**: `DealMergePolicy.merge()`는 두 딜의 수요축 값을 신중히 합성한다 — 한쪽만 알면 아는 값, 서로 다르면 의도적으로 `null`(미상)로 되돌린다. 그런데 `IngestDealsUseCase`의 병합 분기는
  ```java
  DealEvent merged = mergePolicy.merge(existingDomain, candidate);
  existing.applyMerge(merged.priceFirst(), merged.priceMin(), merged.priceMax(), merged.priceLast(),
          merged.crossVerified(), merged.status(), merged.firstSeen(), merged.lastSeen());
  ```
  `merged.demandAxisValue()`를 인자로 넘기지 않는다. `DealEventEntity.applyMerge`는 애초에 `demandAxisValue` 파라미터가 없고, 그 필드를 바꾸는 메서드는 생성자 하나뿐(setter 없음) — 병합 결과 계산값은 버려지고 최초 생성 시점의 옛 값이 영구히 유지된다. 우회 갱신 경로(네이티브 SQL·벌크 UPDATE 등)도 전수 grep으로 확인한 결과 없음.
- **영향**: (1) SPLIT 제품 variant(예: "갤럭시25 256GB")에 블랙 딜이 먼저 생성된 뒤 화이트 딜이 병합되면, 도메인은 `null`(미상)을 계산하지만 엔티티는 "블랙"으로 남아 블랙·화이트가 섞인 표본이 계속 "블랙" 분포의 기준가·신호등에 들어간다 — 이 프로젝트가 반복적으로 경계해 온 SPLIT 표본 오염이 병합 경로에서만 실제로 뚫려 있다. (2) 반대로 `null`(미상)로 시작해 DEMAND_UNKNOWN 큐에 오른 딜이 이후 병합으로 값이 자동 확정돼야 하는데도 영원히 미상으로 남아, 사람이 큐를 수동 처리하기 전까지 그 딜이 SPLIT 분포에서 계속 빠진다. `DealMergePolicy.sameTarget`은 `variantId` 동일성만 보고 축 값은 보지 않아 도달 가능성이 낮지 않다(2사이트 교차검증이 곧 병합이다).
- **권고**: `DealEventEntity.applyMerge`에 `demandAxisValue` 파라미터를 추가하고 `IngestDealsUseCase.confirmDeal`에서 `merged.demandAxisValue()`를 넘긴다. 병합으로 값이 새로 `null`이 되는 경우 `enqueueIfDemandUnknown`과 동일하게 DEMAND_UNKNOWN 큐에 올릴지도 함께 검토.
- **출처**: `raw/A2-usecase.md` A2-01 · 반박 검증 `rebuttal/A2-01.md` → **CONFIRMED**

### BE-02 — collector DB 쓰기 실패 후 rollback 부재로 커넥션이 영구 오염, 연쇄적으로 모든 쓰기가 실패한다 · High · collector-db · ✅수정완료(c67a579)
- **위치**: `collector/src/collector/db/raw_deal_sink.py:54-61`(`upsert_all`, 동일 패턴이 `db/used_listing_sink.py:43-57`·`db/site_poll_state_sink.py:59-87`에도 있음), 트리거 지점 `collector/src/collector/__main__.py:150-159`
- **근거**:
  ```python
  def upsert_all(self, records: list[RawDealRecord]) -> int:
      if not records:
          return 0
      with self.connection.cursor() as cursor:
          cursor.executemany(_UPSERT, [_params(record) for record in records])
      self.connection.commit()
      return len(records)
  ```
  예외 시 `commit()`은 실행되지 않고 `rollback()`은 이 파일을 포함해 저장소 전체(`collector/src`) 어디에도 호출되는 곳이 없다(grep 0건). `__main__.py`의 `_connect_if_needed`가 게시판·중고·폴링상태·별칭 싱크 다섯 개를 **하나의 커넥션**으로 공유하도록 명시적으로 설계돼 있고, `psycopg.connect(...)`는 `autocommit`을 지정하지 않아 psycopg3 기본값(트랜잭션 모드)로 열린다. CHECK 위반(과거 `ENDED` 사고와 동일 계열)이 실제로 `executemany` 도중 예외를 던짐은 `test_raw_deal_sink.py`의 기존 테스트로 실증된다.
- **영향**: 파서가 DB CHECK 제약을 벗어나는 값을 한 번이라도 내면 커넥션이 PostgreSQL의 "current transaction is aborted" 상태로 남는다. 같은 커넥션을 쓰는 `poll_sink.persist_states`·`used_sink.insert_batch`도 즉시 연쇄 실패하고, `SINK_FAILURE_LIMIT=3`이 몇 사이클 안에 `giving_up`→`exit(1)`로 프로세스 전체를 끌고 내려간다. 반박 검증에서 원 발견이 짚지 않은 더 급한 경로도 확인됐다 — `search_source.all_searches()`/`alias_source.all_aliases()`는 try/except로 감싸이지 않아, aborted 상태에서 호출되면 `giving_up` 이벤트조차 없이 트레이스백과 함께 즉시 죽을 수 있다. `restart: on-failure`로 재기동되면 새 커넥션이 열려 회복되지만, 원인이 매 사이클 재발하는 값(파서 버그)이면 crash-loop이 된다.
- **권고**: 각 sink의 쓰기 메서드에서 예외를 잡아 `self.connection.rollback()`을 호출한 뒤 재-raise하거나, `__main__.py`의 각 `except Exception` 블록에서 실패 즉시 커넥션을 rollback한다. "쓰기 실패 → 같은 커넥션으로 다음 쓰기가 성공해야 한다"를 통합 테스트로 추가해 회귀를 잠근다.
- **출처**: `raw/C2-collector-runtime.md` C2-01 · 반박 검증 `rebuttal/C2-01.md` → **CONFIRMED**

### BE-03 — 별칭 사전 substring 다중 히트 시 매칭 결과가 JVM 실행마다 달라질 수 있다 · High · matching · ✅수정완료(9f5f951)
- **위치**: `core/src/main/java/dev/hogumeter/core/domain/matching/AliasDictionary.java:22-27`, `core/src/main/java/dev/hogumeter/core/adapter/persistence/CatalogProjection.java:65-73`, `core/src/main/java/dev/hogumeter/core/domain/matching/Matcher.java:20-24`
- **근거**:
  ```java
  public Optional<Long> match(String joinedTitle) {
      return aliases.entrySet().stream()
              .filter(e -> joinedTitle.contains(e.getKey()))
              .map(Map.Entry::getValue)
              .findFirst();
  }
  ```
  `aliases`는 컴팩트 생성자에서 `Map.copyOf(aliases)`로 감싸지고, `Map.copyOf`류 불변 컬렉션은 반복 순서가 JVM 기동 시 `SALT32L`(해시플러딩 방지 salt)에 따라 달라진다(Javadoc 명시 "unspecified"). `CatalogProjection.aliasDictionary()`도 `HashMap`으로 넘겨 삽입 순서조차 보존하지 않는다. 두 개 이상의 별칭이 같은 제목에 substring으로 동시에 걸리면(계열 제품명, 예: "아이폰17"과 "아이폰17프로") `findFirst()`가 어느 productId를 반환할지 코드 어디에도 규칙이 없다. 같은 패키지의 `DemandAxisSpec.valueIn`은 후보가 둘 이상이면 명시적으로 `null`(미상)을 반환해 대조군 역할을 하는데, `AliasDictionary.match`는 모호성 감지 자체가 없다.
- **영향**: `Matcher.match`는 `dictionary.match`가 반환한 값을 그대로 신뢰해 바로 `MatchResult.confirmed`로 CONFIRMED 확정한다 — 리뷰 큐(CANDIDATE/UNKNOWN)를 거치지 않는다. 도달 경로도 두 갈래다 — `RegisterProductUseCase.register`가 substring 겹침 검증 없이 사람이 입력한 별칭을 그대로 저장하고(제품 라인 등록 시 흔한 패턴), `ResolveReviewItemUseCase.learnAlias`는 원문 제목 전체를 자동 학습해 서로 다른 두 제품의 제목이 우연히 substring 관계가 될 수 있다. 컨테이너 재기동(배포·롤백·OOM)마다 같은 표현의 딜이 다른 제품으로 CONFIRMED될 수 있어, 잘못된 제품의 실가격이 median·P25 기준가 분포를 사람 확인 없이 오염시킨다 — 정직성 원칙(절대 원칙 1) 위반.
- **권고**: `AliasDictionary.match`를 `DemandAxisSpec.valueIn`과 같은 패턴으로 바꾼다 — 서로 다른 productId를 가리키는 별칭이 둘 이상 히트하면 확정하지 말고 미상/후보로 내려보낸다. 최소 변경으로는 "가장 긴 별칭 우선" 규칙을 명시하거나, 다중 히트 시 `Optional.empty()`를 반환해 `Matcher`가 CANDIDATE/UNKNOWN 경로로 보내게 한다.
- **출처**: `raw/A1-domain.md` A1-01 · 반박 검증 `rebuttal/A1-01.md` → **CONFIRMED**

## Medium

### BE-04 — `HttpTelegramApi` 인바운드 경로가 HTTP 상태를 전혀 검사하지 않아 실패가 완전히 침묵한다 · Medium · telegram · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/telegram/HttpTelegramApi.java:89-100`(`getUpdates`), `:102-109`(`answerCallbackQuery`), `:111-117`(`editMessageText`)
- **근거**: `getUpdates`는 `response.statusCode()`를 읽지 않고 바로 `response.body()`를 파싱한다. 텔레그램이 401/403으로 응답해도 본문에 `result` 키가 없어 `parseCallbacks`가 조용히 빈 목록을 반환하고, `TelegramInboundPoller.poll()`의 `catch (RuntimeException)`은 예외 자체가 없어 트리거되지 않는다. `answerCallbackQuery`·`editMessageText`도 `post()`가 돌려주는 상태 코드를 버린다(인터페이스 시그니처 자체가 `void`). `HttpTelegramApi`는 테스트 전무, `TelegramInboundPollerTest`는 `FakeInbound`로 이 파싱 단계 자체를 우회한다.
- **영향**: 봇 토큰 무효화·차단 시 텔레그램 인바운드(승격/기각/무시 버튼) 채널이 흔적 없이 멈추지만, 핵심 파이프라인(수집→기준가→알림 발송)에는 영향이 없고 `ReviewQueueController`의 REST 엔드포인트(`/promote`, `/reject`)가 대안 경로를 제공한다(단 🔕무시는 REST 대안이 없어 텔레그램 단독 의존). 가장 흔한 트리거인 토큰 무효화는 아웃바운드(`sendMessage`)도 함께 401을 받아 `TelegramAlertSender.classify()`가 REJECTED로 `log.error`를 남기므로 완전한 침묵은 아니다. `TelegramInboundPoller`는 `PipelineHealthMonitor`에도 배선돼 있지 않아 인바운드 단독 장애(예: 재배포 시 신·구 컨테이너 중복 기동으로 인한 409 Conflict)는 흔적 없이 지나갈 수 있다.
- **권고**: `getUpdates`도 상태 코드를 먼저 확인해 2xx가 아니면 예외를 던지도록 한다(토큰은 메시지에 담지 않음, SEC-01 유지). `answerCallbackQuery`·`editMessageText`도 실패 시 `log.warn`으로 상태 코드를 남긴다. `TelegramInboundPoller` 쪽에서 연속 실패를 세어 `AdminNotifier`로 알린다.
- **출처**: `raw/B2-adapter.md` B2-01 · 반박 검증 `rebuttal/B2-01.md` → **DOWNGRADED(High→Medium)**

### BE-05 — `_BARE` 가격 폴백이 정규식 백트래킹으로 자기 가드를 우회해 5자리+ 스펙·모델번호를 거짓 가격으로 읽는다 · Medium · collector-price · ❌미해결
- **위치**: `collector/src/collector/pipeline/price.py:55`
- **근거**:
  ```python
  _BARE = re.compile(rf"(?<![\d,])(?<![A-Za-z])(?<![A-Za-z]\s)(\d{{4,}})(?!\s*{_UNIT})(?![A-Za-z])")
  ```
  `\d{4,}`는 탐욕적이라 뒤쪽 부정형 전방탐색이 실패하면 엔진이 자리수를 하나씩 줄여 백트래킹한다. 숫자가 정확히 4자리면 가드가 통하지만, 5자리 이상이면 4자리로 줄인 부분 문자열 뒤가 우연히 숫자라서 가드를 통과해 앞 4자리만 거짓 가격이 된다. 실행 재현 확인됨: `i5-14600K CPU 특가`→`headline_price=1460`, `보조배터리 20000mAh 대용량 충전기`→`headline_price=2000` 등, 전부 `applied_conditions=[]`(태그 없음). 기존 테스트(`test_model_number_followed_by_a_letter_is_not_a_price` 등)는 정확히 4자리 케이스만 검증해 이 우회를 가리고 있었다.
- **영향**: 태그 없이 거짓 가격이 만들어지지만, 같은 variant 표본이 5건 이상 쌓이면 `OutlierDetector`의 Tukey IQR이 극단값을 `OUTLIER_LOWER`로 잡아 리뷰 큐로 보내 median·P25(교차검증 표본)에서 제외한다. 다만 `benchmarkPrice`(median)는 교차검증 여부와 무관하게 `pricingSet` 전체로 계산되고, 표본 5건 미만(SPARSE 신규 등록 제품)이면서 네이버 현재가 미발급(Q-3, `currentPriceProvider`가 상시 `null`)인 지금 같은 상태에서는 대체 판정(`classifyVsCurrent`)도 조용히 스킵돼 방어가 뚫린다. 실 golden fixture 118건 전수에서 이 패턴의 실제 사례는 0건이며, 이 경로는 제목에 원/콤마 가격 표기가 전혀 없어야만 발동해 도달 조건이 좁다. `docs/91` Q-65가 이미 `_BARE`의 같은 근본 원인(문자 인접 가드 실패)·같은 완충 장치·같은 잔여 갭을 다루는 위험군으로 열려 있다.
- **권고**: `\d{4,}`를 원자 그룹으로 감싸 백트래킹을 차단한다: `(?>(\d{4,}))`. 고친 뒤 `14600K`·`20000mAh`·`32768MB` 및 기존 golden 69건 전수(가격·태그 변화 0건)를 회귀 테스트로 추가.
- **출처**: `raw/C1-parser.md` C1-01 · 반박 검증 `rebuttal/C1-01.md` → **DOWNGRADED(High→Medium)**

### BE-06 — `--profile public` 공개 노출이 `preflight.sh prod` 통과에 강제로 묶여 있지 않다 · Medium · security · ❌미해결
- **위치**: `docker-compose.yml:99-120`(`caddy`, `profiles: ["public"]`, 루프백 제한 없는 유일한 진입점), `web/docker-entrypoint.d/40-basic-auth.sh:30-33`, `scripts/preflight.sh:70-77`, `scripts/smoke.sh`(`--profile public`/`caddy` 매치 0건)
- **근거**: `40-basic-auth.sh`는 `WEB_BASIC_AUTH_HTPASSWD`가 비어도 경고 로그만 남기고 web 컨테이너를 정상 기동시킨다(`auth_basic off;`). `caddy`는 web의 인증 상태를 전혀 모른 채 `reverse_proxy web:80`만 한다. `preflight.sh prod`는 이 조합을 FAIL시킬 수 있지만, 이 검사를 통과시키는 것과 실제 `docker compose --profile public up -d` 실행 사이에는 아무 기계적 연결이 없다 — 별도 명령 두 개를 사람이 순서대로 기억해야 한다. `--profile public` 경로는 CI 스모크가 한 번도 실행하지 않는다.
- **영향**: 운영자가 `WEB_BASIC_AUTH_HTPASSWD`를 빠뜨린 채(또는 `preflight.sh prod`를 건너뛰고) `--profile public`을 올리면 인터넷 전체에서 제품 등록·구매기록·기준가 데이터를 인증 없이 읽고 쓸 수 있는 상태가 그대로 기동된다. 발견은 `docker compose logs web`을 사람이 직접 grep해야만 가능하다(`pre-deploy-checklist.md`가 절차는 문서화했으나 강제는 아니다).
- **권고**: CI에 `--profile public` 경로를 격리 스택으로 리허설하는 잡을 추가하거나, `caddy` 서비스에 "auth on" 마커를 확인하는 게이트를 두거나, 최소한 `--profile public up -d` 앞에 `preflight.sh prod`를 강제하는 한 줄짜리 래퍼 스크립트를 만든다.
- **출처**: `raw/B3-security.md` B3-01

### BE-07 — 미상 큐 승격의 부수효과가 원자적 상태 가드보다 먼저 실행된다 · Medium · review-queue · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/ResolveReviewItemUseCase.java:102-114, 199-207`
- **근거**: `promote()`는 ① `readPending`(비원자적 SELECT) → ② `promoteUnclassified`(딜 생성 + 별칭 학습, 부수효과) → ③ `resolve()`의 `UPDATE ... WHERE status='PENDING'`(유일한 원자적 가드) 순으로 실행된다. 클래스 javadoc은 "PENDING 행에만 원자적으로 건다"고 명시하지만 실제 가드는 ③ 하나뿐이고, ①②는 그 이전에 실행된다.
- **영향**: 같은 리뷰 아이템에 대해 텔레그램 인라인 버튼이 짧은 시간 내 두 번 눌리면(전송 지연·콜백 재전달) 두 요청이 거의 동시에 `readPending`을 통과해 둘 다 `promoteUnclassified`(`confirmDeal` 호출 + `learnAlias`)를 실행할 수 있다. `DealMergePolicy.canMerge` 판정 시점 차이로 중복 딜이 생성될 수 있고, `resolve()`는 한쪽만 성공해 이미 실행된 부수효과는 되돌려지지 않는다. 기존 테스트는 순차 이중 처리만 검증하고 동시성(레이스) 시나리오는 없다.
- **권고**: `readPending`부터 `resolve`까지를 하나의 원자적 조건부 업데이트로 묶거나, `promoteUnclassified` 진입 전에 낙관적 잠금/재확인을 추가한다.
- **출처**: `raw/A2-usecase.md` A2-02

### BE-08 — 가격-드리프트(priceless) 알림이 재알림 억제 없이 매 사이클 반복된다 · Medium · collector-drift · ❌미해결
- **위치**: `collector/src/collector/scheduler/drift.py:80-81`(`_healthy`), `:56-77`(`observe`)
- **근거**:
  ```python
  def _healthy(observation: SiteObservation) -> bool:
      return observation.outcome is Outcome.OK and observation.deal_count > 0
  ```
  `_healthy`는 `priced_count`를 전혀 보지 않는다 — 딜은 나오지만 가격이 전부 없는 상태(`deal_count>0, priced_count==0`)도 매번 "건강"으로 판정돼 무장이 즉시 풀리고, 바로 `_diagnose`가 다시 priceless 스트릭을 진단해 재알림을 낸다. zero-yield(`deal_count==0`)는 `_healthy`가 정확히 걸러내 억제가 작동하지만, priceless만 이 경로에서 빠진다. 실행 재현 확인됨 — priceless 연속 10사이클: 알림 8회 반복 vs zero-yield 대조군: 1회로 억제됨.
- **영향**: `drift.py` 문서 자체가 "오늘 찾은 파서 결함 다섯 중 셋이 이 부류(제목 셀렉터만 끊김)"라고 명시할 만큼 흔한 실패 양상인데, 이 상태가 되면 게시판 주기(60초)마다 무한 반복 알림이 로그를 채운다. `drift.py`·`observability.py` 자신이 경계하는 "같은 증상으로 매 사이클 알림이 오면 아무도 안 본다"는 원칙이 코드로는 지켜지지 않는다. 기존 테스트는 zero-yield 억제, 3사이클 alert 존재, 회복 후 재알림만 다루고 "회복 없이 priceless 지속" 시나리오는 없다.
- **권고**: `_healthy`를 진단 원인별로 분리한다 — `_diagnose`의 활성 사유(zero-yield vs priceless)에 맞춰 "그 사유를 실제로 해소했는가"만 무장 해제 조건으로 쓴다.
- **출처**: `raw/C2-collector-runtime.md` C2-02

### BE-09 — `SendDigestUseCase` 발송 후 저장물 갱신 루프에 트랜잭션 경계가 없다 · Medium · digest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/SendDigestUseCase.java:79-97`
- **근거**: `send()`는 `@Transactional`이 아니고, `RecordDigestSentUseCase.recordSent`도 `@Transactional`이 없어 `digestStates.save(...)`가 variant마다 개별 커밋된다. `IncrementDigestQueueAppearancesUseCase.increment`는 별개 트랜잭션이다 — N개 variant 갱신 + 큐 증분이 하나의 원자적 단위가 아니다.
- **영향**: 텔레그램 발송이 전부 성공한 뒤 `recordSent` 루프 도중 DB 예외가 나면 일부 variant의 `digest_state`는 이미 커밋되고 나머지는 반영되지 않는다. 다음 주 다이제스트가 variant마다 다른 기준 시점으로 전환을 판정해 "실제로는 보낸 다이제스트인데 일부만 베이스라인 갱신"되는 불일치가 생긴다 — 클래스 javadoc이 명시한 "전 분할 성공 시에만 저장물을 갱신한다(REL-03 원자성)" 계약이 텔레그램 발송 성공 여부만 게이트할 뿐 DB 쓰기 자체의 원자성은 보장하지 않는다. 기존 테스트는 `allSucceeded=false` 시 전혀 갱신 안 됨만 검증하고 "발송 성공 후 DB 쓰기 도중 일부만 실패"는 다루지 않는다.
- **권고**: `send()`(또는 `recordSent`+`incrementQueueAppearances` 호출부)를 `@Transactional`로 감싸 하나의 트랜잭션으로 묶는다.
- **출처**: `raw/A2-usecase.md` A2-03

### BE-10 — robots.txt가 5xx(서버 오류)를 404(부재)와 동일하게 "전체 허용"으로 처리한다 · Medium · collector-robots · ❌미해결
- **위치**: `collector/src/collector/scheduler/fetcher.py:157-167`(`RobotsGate._load`)
- **근거**:
  ```python
  if status != 200:
      return None  # 404 등 → robots 없음 = 전체 허용
  ```
  `urllib_opener`는 4xx·5xx에서 예외를 던지지 않고 `(status, body)`를 그대로 반환하므로, robots.txt가 500/503을 반환해도 `status != 200` 분기가 404와 5xx를 구분 없이 "전체 허용"으로 접는다. RFC 9309 §2.3.1.3은 robots.txt가 unreachable(5xx)일 때는 404와 달리 보수적으로 다루라고 권고한다.
- **영향**: robots.txt만 일시적으로 500/503을 내고 목록 페이지는 정상 응답하면, `RobotsGate.allows()`가 `True`를 반환해 그 사이클에 실제 페이지를 그대로 긁는다 — "robots.txt를 존중한다"는 원칙(절대 원칙 5)보다 느슨한 기본값이다. 기존 테스트는 404(부재)와 전송 예외(ConnectionError)만 검증하고 robots.txt 자체가 500/503 상태 코드를 반환하는 케이스는 어디에도 없다.
- **권고**: `_load`에서 5xx는 4xx와 분리해 "판정 불가 → 이번 사이클은 보수적으로 disallow(또는 건너뛰기)"로 처리한다.
- **출처**: `raw/C2-collector-runtime.md` C2-03

### BE-11 — `paidPrice=0`인 구매의 관찰 문맥 계산이 0으로 나누기로 예외를 던진다 · Medium · purchase · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/domain/purchase/ObservationContextCalculator.java:24-27`
- **근거**:
  ```java
  long overpaid = purchase.paidPrice() - lowest;
  BigDecimal pct = BigDecimal.valueOf(overpaid)
          .divide(BigDecimal.valueOf(purchase.paidPrice()), 3, RoundingMode.HALF_UP);
  ```
  `purchase.paidPrice()`가 0이면 `ArithmeticException`. `Purchase` 레코드·`RecordPurchaseCommand`·`PurchaseController.record`(`@Valid` 없음) 어디에도 `paidPrice` 검증이 없다. `DealTags.FREE_PRICE`(0원 무료 배포)가 이미 1급 개념으로 존재해 "0원에 샀다"는 입력이 현실적으로 발생할 수 있다.
- **영향**: `POST /api/v1/purchases`로 `paidPrice: 0`을 기록하면 저장은 성공하지만(막는 검증 없음), 이후 같은 variant에 활성 딜이 생기면 `GetPurchaseObservationsUseCase`가 `ArithmeticException`으로 500이 나 그 사용자의 구매 관찰 목록 조회 API 전체가 죽는다(판단 화면이 그 구매를 아예 못 보여준다). 기존 테스트는 전부 양수 `paidPrice`만 쓴다.
- **권고**: `Purchase` 컴팩트 생성자(또는 `RecordPurchaseCommand`)에 `paidPrice >= 0` 검증을 추가하거나, `ObservationContextCalculator.compute`에서 `paidPrice == 0`일 때 퍼센트를 `null`로 두고 원화 상회분만 낸다.
- **출처**: `raw/A1-domain.md` A1-02

### BE-12 — `POST /purchases`에 `purchasedAt` 없이 보내면 NPE로 500 · Medium · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseUseCase.java:84-88`
- **근거**:
  ```java
  Optional<Instant> observedFrom = deals.stream().map(DealEvent::firstSeen).min(Instant::compareTo);
  if (observedFrom.isPresent() && cmd.purchasedAt().isBefore(observedFrom.get())) { ... }
  Clock asOf = Clock.fixed(cmd.purchasedAt(), clock.getZone()); // purchasedAt null이면 NPE
  ```
  `RecordPurchaseCommand.purchasedAt`(참조타입 `Instant`)이 요청 바디에서 빠지면 `null` — 표본 유무와 무관하게 양쪽 경로 모두 NPE.
- **영향**: `purchasedAt` 누락 요청이 `NullPointerException`→미매핑→500으로 떨어진다. `@Transactional`이라 데이터 손상은 없지만 "purchasedAt이 필요합니다" 같은 명확한 응답을 못 준다. 기존 테스트(`PurchaseEndpointTest` 등)는 전부 `purchasedAt`을 채워 보내 이 경로를 검증하지 않는다.
- **권고**: `RecordPurchaseCommand` 컴팩트 생성자 또는 `RecordPurchaseUseCase.record()` 진입부에서 `purchasedAt == null` 시 400 거절 예외를 던진다(BE-20의 `paidPrice` 검증과 함께 정리 가능).
- **출처**: `raw/B1-web.md` B1-02

### BE-13 — `PUT .../comparison-axes`에 `names` 없이 보내면 NPE로 500 · Medium · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/ComparisonAxisController.java:27`, `core/src/main/java/dev/hogumeter/core/application/DefineComparisonAxesUseCase.java:25-27`
- **근거**:
  ```java
  public record AxesRequest(List<String> names) {}
  // DefineComparisonAxesUseCase
  public List<ComparisonAxisEntity> ensure(long productId, List<String> names) {
      for (String name : names) { ... }   // names == null이면 NPE
  ```
  Jackson은 JSON에 없는 필드를 `null`로 채우고, `AxesRequest`·`ensure()` 어디에도 방어 코드가 없다.
- **영향**: `PUT /api/v1/products/{productId}/comparison-axes` 바디 `{}`(`names` 누락) → NPE → 미매핑 → Spring 기본 500(`{code,message}` 계약 붕괴). 관련 테스트(`UsedComparisonEndpointsTest`)는 전부 `names`를 채워 보내 누락 케이스가 없다.
- **권고**: `AxesRequest` 컴팩트 생성자나 `ensure()` 시작부에서 `names == null`이면 빈 목록으로 정규화하거나 400 거절 — `UsedSearchController.orEmpty(...)`가 이미 같은 문제를 해결한 패턴.
- **출처**: `raw/B1-web.md` B1-01

### BE-14 — `productId` 미검증인 두 컨트롤러가 FK 위반을 그대로 500으로 흘린다 · Medium · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RegisterUsedSearchUseCase.java:29-35`, `core/src/main/java/dev/hogumeter/core/application/DefineComparisonAxesUseCase.java:24-31`
- **근거**: `used_search.product_id`·`comparison_axis.product_id` 둘 다 `references product (id)` FK가 걸려 있는데, 두 유스케이스 모두 다른 유스케이스(`RegisterProductUseCase.validate` 등)가 쓰는 `products.existsById(...)` 선검사가 없다.
- **영향**: `POST /api/v1/products/999999/used-searches`(존재하지 않는 productId) → INSERT가 FK 위반 → `DataIntegrityViolationException` → 미매핑 → 500. `PUT .../comparison-axes`도 동일. 기존 테스트(`UsedSearchControllerTest` 등)는 GET에서만 미존재 productId를 시험하고 POST/PUT은 시험하지 않는다.
- **권고**: 두 유스케이스 시작부에 `if (!products.existsById(productId)) throw new ProductNotFoundException(productId);` 추가(기존 예외 재사용, 404/`PRI_PRODUCT_NOT_FOUND`).
- **출처**: `raw/B1-web.md` B1-03

### BE-15 — `ApiExceptionHandler`에 미매핑 예외 catch-all이 없어 `{code,message}` 계약이 케이스별로 깨진다 · Medium · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/ApiExceptionHandler.java`(166줄 전체 — `@ExceptionHandler(Exception.class)`류 없음)
- **근거**: 이 프로젝트의 유일한 `@RestControllerAdvice`다. BE-12~14가 보여주듯 `NullPointerException`·`DataIntegrityViolationException`은 여기 안 걸리고 Spring Boot 기본 오류 응답으로 떨어진다(내부정보 유출은 없음 — 기본값이 안전하기 때문).
- **영향**: 사용자는 "입력 누락"과 "DB 자체 오류"를 구분 못 하고 둘 다 `HTTP_500`으로만 본다 — 절대 원칙 6(과대약속 금지)과 반대로 "무엇이 왜 실패했는지"를 숨기는 쪽으로 작동한다. 이 핸들러에 대한 전용 테스트가 없어 미매핑 경로 전체가 검증되지 않는다.
- **권고**: `@ExceptionHandler({DataIntegrityViolationException.class, NullPointerException.class})` 같은 최종 방어선을 추가하되, 근본 수정은 BE-12~14처럼 발생 지점에서 400으로 막는 것이 우선이다(이 항목은 회귀 안전망).
- **출처**: `raw/B1-web.md` B1-04

### BE-16 — `DealEventMapper`의 "대표 원문" 선택이 정렬 없이 이뤄져 재현 불가능 · Medium · persistence · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventMapper.java:33-38`, `core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventSourceRepository.java:6-9`
- **근거**:
  ```java
  List<DealEventSourceEntity> src = sources.findByDealEventId(e.getId());
  if (!src.isEmpty()) {
      DealEventSourceEntity representative = src.get(0);
      site = representative.getSite();
      sourceUrl = rawPosts.findById(representative.getRawDealPostId()).map(RawDealPost::getUrl).orElse("");
  }
  ```
  `findByDealEventId`는 `order by`가 없는 파생 쿼리라 PostgreSQL이 행 순서를 보장하지 않는데 `src.get(0)`이 이를 "대표"로 쓴다. `DealMergePolicy`는 "대표"를 firstSeen이 이른 쪽으로 명시적으로 계산하고, 같은 문제를 겪는 `GetReviewQueueUseCase`의 네이티브 SQL은 `order by des.id limit 1`로 명시 정렬한다 — `DealEventMapper`만 정렬을 생략했다.
- **영향**: 두 사이트에서 교차검증된 딜의 소스 물리적 저장 순서가 바뀌면, 같은 딜을 두 번 조회했을 때(알림 발송 시점 vs 웹에서 다시 볼 때) 서로 다른 사이트의 링크가 나올 수 있다 — 절대 원칙 2가 전제하는 "이 근거는 안정적이다"가 깨진다. `site()`/`sourceUrl()` 값 자체를 단언하는 테스트가 코드베이스에 하나도 없다.
- **권고**: `DealEventSourceRepository.findByDealEventId`에 `OrderBy`를 추가한다(`findByDealEventIdOrderByIdAsc`가 간단). `DealMergePolicy`의 의도에 맞추려면 원문의 firstSeen으로 정렬. 정렬 기준을 정한 뒤 `site()`/`sourceUrl()`을 단언하는 회귀 테스트를 추가.
- **출처**: `raw/A3-persistence.md` A3-01

### BE-17 — `ReprocessDealPricesUseCase`가 원문 조회에서 N+1을 낸다(형제 클래스는 배치 조회를 쓴다) · Medium · pipeline-reprocess · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/ReprocessDealPricesUseCase.java:108-117`
- **근거**:
  ```java
  private List<PriceEvidence> evidenceFor(DealEventEntity deal) {
      return sources.findByDealEventId(deal.getId()).stream()
              .map(DealEventSourceEntity::getRawDealPostId)
              .map(rawPosts::findById)   // 소스마다 개별 조회
              ...
  }
  ```
  매 틱마다 전체 활성(REFRESHABLE) 딜을 돌며 소스 하나당 `rawPosts.findById`를 개별 호출한다. 바로 옆의 `ReprocessDealStatusUseCase.endIfAllSourcesEnded`는 같은 문제를 `rawPosts.findAllById(rawIds)`로 배치 조회해 회피하고 있어 이 파일만 일관성이 깨진 상태다.
- **영향**: 딜 수가 늘수록(예: 200건 × 평균 소스 2건) 매 60초 틱마다 400회 이상의 개별 쿼리가 발생한다. 다른 파일들(`GetPrioritizedProductsUseCase` 등)은 "1인용 규모라 N+1을 그대로 둔다(PERF-04)"는 근거를 명시했는데, 이 파일에는 그런 근거가 없고 형제 클래스는 이미 배치 조회로 회피하고 있어 의도된 결정이라기보다 누락으로 보인다.
- **권고**: `evidenceFor`를 `sources.findByDealEventId` 결과에서 `rawDealPostId` 목록을 모은 뒤 `rawPosts.findAllById(ids)` 한 번으로 바꾼다.
- **출처**: `raw/A2-usecase.md` A2-04

### BE-18 — `RawDealPostUpserter`가 Spring 빈으로 배선되지 않아 프로덕션에서 전혀 실행되지 않는다 · Medium · persistence · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPostUpserter.java:1-25`, `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPostRepository.java:10`
- **근거**: 클래스 선언에 `@Component`/`@Service` 등이 없다. `grep -rn "\.upsert(" core/src/main/java/` 결과 호출부 0건 — `new`하는 곳은 테스트뿐이다. 그런데 클래스 javadoc은 "멱등 수집 업서트(BM-01 AC-1/AC-2)... UNIQUE 제약이 최종 방어선"이라고 적어 실제 수집 경로의 멱등성 보증 지점인 것처럼 서술한다. 실제 프로덕션 쓰기 경로는 collector(Python) `raw_deal_sink.py`가 담당하며 이 Java 클래스와는 완전히 별개 구현이다.
- **영향**: 나중에 누군가 재수집 갱신 로직을 고치려 이 클래스가 "그 로직이다"라고 오인하고 수정한 뒤 `RawDealPostUpsertTest`가 GREEN인 것을 보고 배포해도, 프로덕션 동작에는 아무 영향이 없다 — 테스트는 초록인데 운영 동작은 그대로인 상태가 발견되지 않은 채 남는다. `check-table-wiring.sh`·`check-repository-readers.sh` 둘 다 "빈으로 실제 도달 가능한가"는 범위 밖이라 이 상태를 못 잡는다.
- **권고**: 이 클래스가 정말 필요 없다면(collector가 유일한 쓰기 경로라는 현재 계약이 맞다면) 삭제하고 `RawDealPostUpsertTest`도 제거한다. 문서화용 참조 구현으로 남기려는 의도였다면 javadoc 첫 줄에 "프로덕션 미배선"이라고 명시한다.
- **출처**: `raw/A3-persistence.md` A3-02

### BE-19 — `parse_bunjang`이 일부 필드만 `.get()`으로 방어하고 `pid`·`update_time`은 직접 인덱싱해, 항목 하나의 결측이 그 사이클 전체를 삼킨다 · Medium · collector-parser · ❌미해결
- **위치**: `collector/src/collector/parsers/bunjang.py:23,34`
- **근거**:
  ```python
  pid = str(item["pid"])
  ...
  posted_at=datetime.fromtimestamp(int(item["update_time"]), tz=timezone.utc),
  ```
  같은 함수 안에서 `price`·`status`·`num_faved`·`name`은 `.get(...)`로 방어하는데 `pid`·`update_time`만 직접 인덱싱한다. 실행 재현 확인됨 — 목록 중 한 항목만 `update_time`이 없어도 `KeyError`가 그 루프 전체를 중단시켜 정상 항목까지 그 사이클에서 전부 유실된다.
- **영향**: 다른 3개 파서(뽐뿌·루리웹·펨코)는 셀렉터가 못 찾은 행을 `continue`로 건너뛰어 정상 행은 살리는 반면, bunjang은 한 항목의 결측이 배치 전체를 죽이는 비대칭 구조다. `loop.py::_poll`이 예외를 `TRANSIENT`로 흡수해 프로세스는 안 죽지만, `parse_bunjang`은 현재 실 폴링 레지스트리(`hotdeal_boards()`)에 없어(M2 대기) 지금 당장 프로덕션 영향은 없다 — 다만 `docs/98-field-notes.md`가 배선이 임박했음을 시사한다.
- **권고**: `item.get("pid")`/`item.get("update_time")`도 방어적으로 읽고, 결측 개별 항목은 파서 전체를 죽이지 않고 건너뛰며 카운터로 세는 것을 권한다(다른 파서와 통일). 배선 전에 결측 필드 케이스를 fixture/합성 테스트로 추가.
- **출처**: `raw/C1-parser.md` C1-02

## Low

### BE-20 — `RecordPurchaseCommand.paidPrice`가 음수/0이어도 그대로 저장된다 · Low · purchase · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseUseCase.java:57-73`, `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseCommand.java:16`
- **근거**: `record()` 전체에 `cmd.paidPrice()` 범위 검증이 없다. `InvalidCoupangObservationException`·`AlertPolicySettings`가 이미 같은 종류의 검증을 이 코드베이스의 관례로 삼고 있는데 구매 기록만 빠졌다.
- **영향**: `paidPrice: -1`(또는 `0`)이 정상 201로 저장되면, 성적표·`snapPaidGap` 등이 말이 안 되는 값을 영구적으로 담는다(구매 기록은 수정 API가 없어 되돌리기 어렵다).
- **권고**: `RecordPurchaseUseCase.record()` 진입부에 `paidPrice <= 0` 거절 추가(BE-12와 함께 정리 가능).
- **출처**: `raw/B1-web.md` B1-05

### BE-21 — `DigestScheduler.tick()`에 예외 격리가 없다 · Low · scheduler · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/scheduler/DigestScheduler.java:37-41`
- **근거**: `PipelineScheduler`는 각 단계를 `runStep`으로 감싸 실패를 격리·집계하지만, `DigestScheduler`는 `sendDigest.send()`가 던지는 `RuntimeException`을 전혀 잡지 않는다. Spring 기본 `LoggingErrorHandler`만 로그를 남기고 이 클래스 자신의 로거·`AdminNotifier`에는 기록이 없다.
- **영향**: 다이제스트 조립 중 DB 일시 장애가 나면 그 주 다이제스트가 조용히 안 나가고 다음 주까지 아무도 모른다.
- **권고**: `try/catch`로 감싸 `AdminNotifier.notify(...)`를 호출해 `PipelineScheduler`와 같은 관측 수준을 맞춘다.
- **출처**: `raw/B2-adapter.md` B2-03

### BE-22 — `getUpdates`가 콜백이 아닌 업데이트를 로컬에서 완전히 건너뛰어 offset이 정체될 수 있다 · Low · telegram · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/telegram/HttpTelegramApi.java:130-133`
- **근거**: 콜백이 아닌 업데이트(일반 텍스트·`/start` 등)는 목록에 실리지 않아 `TelegramInboundPoller.poll()`이 그 update_id로 offset을 전진시키지 않는다.
- **영향**: 사용자가 봇에게 일반 메시지를 보내면 콜백이 올 때까지 매 3초 폴마다 텔레그램에서 그 update_id가 계속 재조회된다(부작용 없는 낭비 트래픽).
- **권고**: 콜백 여부와 무관하게 응답의 모든 update의 최대 `update_id`로 offset을 전진시킨다.
- **출처**: `raw/B2-adapter.md` B2-02

### BE-23 — `CoupangObservationController`의 429 분기에 HTTP 레벨 테스트가 없다 · Low · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/CoupangObservationController.java`
- **근거**: GET/POST 인증 비대칭 자체는 이 프로젝트의 "GET은 앱 레벨 인증 없음, nginx가 경계" 설계와 일치해 실결함이 아니지만(B1 판정, 아래 "검토했으나 문제없음" 참고), 조사 과정에서 `RateLimitExceededException`(429) 분기는 `CoupangObservationControllerTest`의 5개 테스트 모두 요청 1회씩만 보내 레이트리밋 창을 채우지 않아 컨트롤러 배선(429 상태코드 + `RATE_LIMIT_EXCEEDED` 코드가 실제로 나가는지)이 미검증 상태로 남아 있음이 확인됐다.
- **영향**: 순수 도메인(`FixedWindowRateLimiterTest`)은 잠겨 있으나 컨트롤러 레벨 배선 회귀는 못 잡는다.
- **권고**: 레이트리밋 창을 채우는 MockMvc 테스트를 하나 추가해 429 응답 코드·바디를 관통 검증한다.
- **출처**: `raw/B1-web.md` B1-06(부수 발견)

### BE-24 — `GlobalSettingsController` REST 계약에 core 쪽 HTTP 레벨 테스트가 없다 · Low · rest · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/GlobalSettingsController.java`
- **근거**: core 쪽엔 `GlobalSettingsController`에 대한 MockMvc/`@SpringBootTest` 테스트가 없다(서비스 계층 `GlobalExcludeKeywordsTest`만 존재). 리뷰 지시문이 전제한 "web 테스트 0건"은 실측 결과 틀렸다 — `SettingsPage.test.tsx`가 API 모듈을 모킹해 화면 동작은 검증하지만, `GET/PUT /api/v1/settings/exclude-keywords`의 경로·JSON 바인딩·상태코드를 관통하는 core 테스트는 0건이다.
- **영향**: 컨트롤러 로직 자체는 단순해 지금 깨진 것은 없지만, 경로 오타·HTTP 메서드 실수·JSON 바인딩 회귀가 나면 core 테스트 스위트가 못 잡는다.
- **권고**: `GlobalSettingsControllerTest`(`@SpringBootTest`+MockMvc) 하나 추가 — GET 기본값·PUT 정규화 왕복·빈 배열 저장 세 케이스.
- **출처**: `raw/B1-web.md` B1-07

### BE-25 — 목록류 GET 엔드포인트가 상한 없이 전체를 반환한다 · Low · rest · ❌미해결
- **위치**: `ProductQueryController.java:20`, `PriorityController.java:33`, `ReviewQueueController.java:34`, `WatchController.java:36,42`
- **근거**: 전부 `List<...>`를 페이지네이션 파라미터 없이 그대로 반환한다.
- **영향**: 1인용·수십~수백 건 규모에서는 지금 당장 리스크가 낮으나, 미상 큐나 `resolved` 회고 탭이 시간이 지나며 무한정 쌓이는 종류라 향후 성능 이슈 후보다.
- **권고**: 지금 당장 조치 불필요. 체감 지연이 생기면 그때 `LIMIT`/커서 페이지네이션 도입.
- **출처**: `raw/B1-web.md` B1-08

### BE-26 — core·collector 컨테이너가 root로 실행된다 · Low · security · ❌미해결
- **위치**: `core/Dockerfile:9-12`, `collector/Dockerfile:1-10`(둘 다 `USER` 지시 없음)
- **근거**: 두 Dockerfile 모두 기본 이미지의 root로 프로세스가 돈다. `docker-compose.yml`에 `docker.sock` 마운트·`privileged`·`cap_add`·`network_mode: host`가 없어 컨테이너 탈출 경로는 열려 있지 않다.
- **영향**: RCE급 취약점이 터지면 컨테이너 안에서 root 권한을 얻지만, 탈출 경로 부재로 피해 반경은 "그 컨테이너 안"으로 제한된다.
- **권고**: 두 Dockerfile에 비루트 `USER` 추가(`RUN useradd -r appuser` + `USER appuser`).
- **출처**: `raw/B3-security.md` B3-02

### BE-27 — 모든 시크릿이 컨테이너 환경변수로만 전달된다(파일/Docker secret 미사용) · Low · security · ❌미해결
- **위치**: `docker-compose.yml:11-13,33-50,85,124-130` — `DB_PASSWORD`·`TELEGRAM_BOT_TOKEN`·`NAVER_CLIENT_ID/SECRET`·`EXTENSION_INGEST_TOKEN`·`WEB_BASIC_AUTH_HTPASSWD` 전부 `environment:` 참조
- **근거**: `docker inspect` 또는 `/proc/<pid>/environ`으로 평문이 그대로 보인다. Docker `secrets:`(파일 마운트) 방식과 달리 접근 통제가 "docker 데몬에 닿을 수 있는가"로 뭉뚱그려진다.
- **영향**: EC2 호스트에 SSH 접근 권한을 가진 사람은 `docker inspect` 한 번으로 모든 시크릿을 평문 확인할 수 있다. 다만 그 사람은 이미 `.env` 파일 자체도 읽을 수 있으므로(같은 호스트, 같은 신뢰 경계) 이 항목이 추가로 여는 공격면은 사실상 없다.
- **권고**: 지금 우선순위로 올릴 근거는 약함. 여러 사람이 같은 호스트를 공유하게 되면 Docker secrets나 EC2 Secrets Manager 인젝션을 재검토.
- **출처**: `raw/B3-security.md` B3-04

## Info

### BE-28 — SEC-03(텔레그램 인바운드 화이트리스트) 관련 문서 2곳이 실제 구현과 어긋난다 · Info · security · ❌미해결
- **위치**: `.env.example:39-41`, `working-area/pre-deploy-checklist.md:27` (둘 다 "아직 인바운드 핸들러가 없어 소비되지 않는다"고 서술)
- **근거**: 실제로는 `TelegramInboundPoller`·`ReviewCallbackRouter`가 이미 구현돼 있고, `TELEGRAM_ALLOWED_CHAT_IDS`가 비면 `TELEGRAM_CHAT_ID`로 폴백하며 둘 다 비면 빈 허용집합(아무도 허용 안 함)으로 닫힌다 — 코드는 안전한 방향으로 이미 앞서 있고 문서만 뒤처졌다.
- **영향**: 위험한 방향의 드리프트는 아니다(코드가 문서보다 안전). 다만 다음 세션이 문서를 믿고 설정 검토를 소홀히 하거나, 이미 끝난 구현을 다시 하려 시간을 쓸 여지가 있다.
- **권고**: 두 파일의 해당 문장을 "구현됨, 닫힌 기본값"으로 갱신.
- **출처**: `raw/B3-security.md` B3-03

### BE-29 — `SchedulingConfig` 주석이 폐기된 모듈 소유권 구분을 인용한다 · Info · scheduler · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/scheduler/SchedulingConfig.java:9-10`
- **근거**: `"core는 상대 개발자 영역이라 기존 파일 수정 없이 additive로만 들어간다"` — `CLAUDE.md` 모듈 소유권 절은 2026-07-23부로 이 구분을 폐기했다고 명시한다.
- **영향**: 기능에는 영향 없는 주석 드리프트.
- **권고**: 주석을 현재 모듈 소유권 절에 맞게 갱신하거나 삭제.
- **출처**: `raw/B2-adapter.md` B2-04

### BE-30 — `site_poll_state_sink.py`가 `datetime`을 임포트하지 않는다 · Info · collector-db · ❌미해결
- **위치**: `collector/src/collector/db/site_poll_state_sink.py:59`(`def persist_states(self, states: Mapping[str, SiteState], now: datetime) -> int:`)
- **근거**: 파일 상단 임포트에 `datetime`이 없다. `from __future__ import annotations`로 어노테이션이 지연 평가(문자열)되므로 `typing.get_type_hints()`를 부르지 않는 한 런타임 오류는 없다(실측: 아무도 그걸 부르지 않는다).
- **영향**: 실패 시나리오를 구성할 수 없다 — 정적 분석 도구·향후 리팩터를 위한 정직성 문제.
- **권고**: `from datetime import datetime` 추가.
- **출처**: `raw/C2-collector-runtime.md` C2-04

## 검토했으나 문제없음(통합)

- **`CoupangObservationController` GET/POST 인증 비대칭**: 이 프로젝트는 앱 레벨 인증을 아예 두지 않고 nginx Basic Auth라는 경계 한 겹으로 전체를 막는 설계다(GET 계열 25개 엔드포인트 전체가 동일). POST에만 토큰을 요구하는 이유는 "쓰기 전 최소 검증"이지 조회 차단이 목적이 아니다. 의도된 설계로 판정. (`raw/B1-web.md` B1-06)
- **`AlertEvaluator`/`AlertIntensity`/`AlertGate`/`QuietHours`**: 강도 서열·ENDED 딜 억제(Q-27③)·quiet hours Asia/Seoul 시간대(커밋 `2baf21a`로 수정 완료) 모두 문서와 일치. (`raw/A1-domain.md`)
- **`DealEvent`/`DealStatus`/`DealMergePolicy`/`DealEventEntity` 상태기계**: `DealMergePolicy.merge`가 계산한 상태를 엔티티 계층이 `canTransitionTo`/`transitionTo`로 재검증하는 안전망이 살아있음을 호출자 추적으로 확인. (`raw/A1-domain.md`)
- **`DealSets`(pricingSet/occurrenceSet/signalSet)**: 배송비미상·무료가·이상치·영구제외 필터가 문서(Q-46②, D-5)와 일치. (`raw/A1-domain.md`)
- **`Purchase`/`PurchaseState`/`ReportCardCalculator`**: 만료 전이가 스케줄러에서 실제 호출되고, n=0을 나눗셈 전에 분기해 0-나눗셈 없음. (`raw/A1-domain.md`)
- **`used/*` 도메인 20파일**: `PriceContextCalculator`·`ListingDiff`·`UsedMatcher`·`UsedAlertPolicy`·`UsedRiskSignals` 모두 문서(docs/used/04 AC)와 일치, 0-나눗셈·비결정성 없음. (`raw/A1-domain.md`)
- **도메인 전체 `Clock` 주입 규약**: `Instant.now()`/`LocalDate.now()`/`System.currentTimeMillis()` 등 직접 호출 0건, 컬렉션 방어적 복사(`List.copyOf` 등) 전수 확인. (`raw/A1-domain.md`)
- **`IngestDealsUseCase.candidateFrom`의 `dealEventId=0L` 자리표시자**: 실제로 0L이 읽히는 프로덕션 경로 없음(병합/신규 양쪽 모두 실제 DB id로 대체됨). (`raw/A2-usecase.md`)
- **벌크 UPDATE 후 `entityManager.refresh()` 패턴**(`ExpirePurchaseObservationsUseCase` 등): 영속성 컨텍스트 우회를 스스로 정정, 옛 값 재조회 위험 없음. (`raw/A2-usecase.md`)
- **`FlushHeldAlertsUseCase`·`FollowUpAlertUseCase` 멱등성**: `(deal_event_id, kind)` 존재 확인 후 저장, 유실 없이 순환. (`raw/A2-usecase.md`)
- **`GetPrioritizedProductsUseCase`·`GetWatchItemsUseCase`·`VariantExcludeKeywords`의 N+1**: 1인용 규모(PERF-04) 근거 명시 또는 호출 빈도가 낮아 별도 결함으로 올리지 않음(BE-17과 대비됨). (`raw/A2-usecase.md`)
- **`ReviewCallbackRouter`의 SEC-03 화이트리스트**: `allowedChats`가 비면 닫힌 기본값, 예외도 `CallbackResult`로 흡수돼 폴러가 안 죽음. (`raw/A2-usecase.md`)
- **엔티티↔DDL 타입 정합 전수(24개)**: `ddl-auto=validate`로 구조적 보증, enum ↔ CHECK 제약 전수 일치, V↔R 23쌍 대칭성 확인. (`raw/A3-persistence.md`)
- **네이티브 SQL 6곳**: 전부 파라미터 바인딩만 사용(인젝션 없음), 정렬 필요한 곳은 `collate "C"`로 로케일 의존성 제거 완료. (`raw/A3-persistence.md`)
- **`HealthController`/`HealthReport`**: 예외 메시지 대신 타입 이름만 노출, DB 프로브 타임아웃이 compose 대기시간보다 짧음. (`raw/B1-web.md`)
- **`AlertPolicyController`/`AlertPolicySettings`**: `targetPrice`·`periodMonths`·`kDisplay`·`quietHours` 전부 compact constructor에서 400으로 막힘 — 검증 모범 사례. (`raw/B1-web.md`)
- **`ProductQueryController`/`UsedSearchController` GET 빈 목록**: 없는 productId에 404 대신 빈 목록을 반환하는 것은 의도된 sentinel-free 계약. (`raw/B1-web.md`)
- **CORS 미설정**: 동일 오리진 전제와 일치, 결함 아님. (`raw/B1-web.md`)
- **SEC-01 토큰 유출 없음**: `TelegramTransportException`이 원인 예외의 클래스 이름만 담아 토큰이 포함된 URL이 로그에 노출되지 않음. (`raw/B2-adapter.md`)
- **텔레그램 HTML/Markdown 이스케이프**: `parse_mode` 미지정으로 순수 텍스트 처리돼 이스케이프 자체가 불필요. (`raw/B2-adapter.md`)
- **SEC-08 상태 분류(아웃바운드)**: `TelegramAlertSender.classify()`의 2xx/5xx/기타 분류가 파라미터화 테스트로 정확히 잠김. (`raw/B2-adapter.md`)
- **파이프라인 단계 순서·격리·카운터(`PipelineScheduler`)**: 20여 개 테스트로 순서·예외격리·"0 리포트 대신 미출력" 계약 촘촘히 검증. (`raw/B2-adapter.md`)
- **확장 ingest 인증**: 토큰 미설정 시 무조건 거절, 상수시간 비교, 인증 통과 후에만 레이트리밋 소비. (`raw/B3-security.md`)
- **actuator 미노출·에러 응답 기본값·htpasswd 파일 권한(640)·시크릿 스캔(gitleaks)**: 전부 안전한 기본값이거나 실행 가능한 계약 테스트로 지켜짐. (`raw/B3-security.md`)
- **뽐뿌 EUC-KR/cp949 디코딩**: strict 디코딩으로 "조용히 깨진 제목"이 DB에 들어가지 않음. (`raw/C1-parser.md`)
- **CSS 셀렉터 부재 시 침묵**: 3개 bs4 파서 모두 조용한 빈 결과를 반환하고 `scheduler/drift.py`가 이를 감지하도록 설계(단, BE-08이 그 감지 자체의 재알림 억제 결함을 지적). (`raw/C1-parser.md`)
- **루리웹 `[종료]` 마커**: 제목 앵커 안/밖을 정확히 가르고 golden 28건 중 3건만 SOLD_OUT으로 잡힘이 테스트로 고정. (`raw/C1-parser.md`)
- **배송비 "모름" 사일런트 처리 없음**: `classify_shipping`의 모든 분기가 최종적으로 `SHIPPING_UNKNOWN`+설명 태그로 떨어짐. (`raw/C1-parser.md`)
- **중고/신품 소스 분리**: `check-source-vocabulary.sh`가 각 파서를 정확히 한쪽 허용집합에만 두도록 강제. (`raw/C1-parser.md`)
- **값 계약 정본-사본 드리프트 방지**: `check-tag-contract.sh`가 collector/core/web 세 곳의 문자열 리터럴 일치를 강제. (`raw/C1-parser.md`)
- **opt-in 게이트·사이트 격리·SIGTERM 처리**: `ALLOW_NETWORK_ENV` 미설정 시 네트워크 객체 자체가 생성되지 않고, 한 사이트 실패가 다른 사이트 루프를 끊지 않으며, `Event.wait` 기반 정지로 PEP 475 유예시간 문제 회피. (`raw/C2-collector-runtime.md`)
- **UA·robots 준수(위장 없음)**: `USER_AGENT`가 목적을 명시하고, stdlib이 못 잡는 와일드카드 Disallow도 자체 매처가 정확히 처리(2026-07-22 실측 기반). (`raw/C2-collector-runtime.md`)
- **SQL 파라미터 바인딩(collector 전체)**: 모든 sink가 `%(name)s` 바인딩만 사용, 문자열 조립 없음. (`raw/C2-collector-runtime.md`)
- **워터마크 갱신 순서**: 적재 성공 후에만 커서를 전진시켜 "쓰기 실패인데 워터마크만 전진"하는 거짓-신선도 경로 없음(단, BE-02의 커넥션 오염이 겹치면 워터마크 자체가 안 갱신돼 실패가 정직하게 드러남). (`raw/C2-collector-runtime.md`)

## 리뷰어별 원시 산출물

| 리뷰어 | 파일 | 발견 건수(High/Medium/Low/Info) |
|---|---|---|
| A1(core 순수 도메인) | `raw/A1-domain.md` | 1/1/0/2 |
| A2(core 애플리케이션) | `raw/A2-usecase.md` | 1/3/1/0 |
| A3(core 영속성·Flyway) | `raw/A3-persistence.md` | 0/2/0/3 |
| B1(core REST 어댑터) | `raw/B1-web.md` | 0/4/4/3 |
| B2(텔레그램·스케줄러) | `raw/B2-adapter.md` | 1/0/2/1 |
| B3(보안 표면) | `raw/B3-security.md` | 0/1/2/2 |
| C1(collector 파서·파이프라인) | `raw/C1-parser.md` | 1/1/0/1 |
| C2(collector 수집 런타임) | `raw/C2-collector-runtime.md` | 1/2/0/1 |
