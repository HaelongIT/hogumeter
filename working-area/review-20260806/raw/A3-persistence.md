# A3 — core 영속성 + Flyway 마이그레이션 리뷰

## 요약 (High 0 / Medium 2 / Low 0 / Info 3)

53개 파일(엔티티 24 + 리포지토리 25 + `RawDealPost`·`RawDealPostUpserter`·`DealEventMapper`·`CatalogProjection`)과
`V1~V23`/`R1~R23` 마이그레이션 전량을 대조했다. `ddl-auto=validate`가 매 컨텍스트 로드마다 엔티티↔DDL 타입을
검증하는 구조 덕에(`application.yml:10`) 타입 불일치·nullable 불일치·enum 값 불일치는 전무했고, V↔R 23쌍 모두
대칭적으로 되돌린다. 네이티브 SQL(6곳)은 전부 `?`/`:param` 바인딩이고 로케일 의존 정렬은 이미 `collate "C"`로
고정돼 있었다. 발견된 두 건은 데이터 손상이 아니라 **"의도한 계약이 코드로 강제되지 않는" 부류**다 — 하나는
"대표 원문 링크"가 정렬 없는 쿼리로 결정돼 재현 불가능하고, 하나는 그 이름이 주장하는 안전장치가 실제로는
프로덕션 어디에도 배선돼 있지 않다.

### A3-01 — `DealEventMapper`의 "대표 원문" 선택이 정렬 없이 이뤄져 재현 불가능 · Medium
- **위치**:
  - `core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventMapper.java:33-38`
  - `core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventSourceRepository.java:6-9`
- **근거**:
  `DealEventMapper.toDomain()`:
  ```java
  List<DealEventSourceEntity> src = sources.findByDealEventId(e.getId());
  ...
  if (!src.isEmpty()) {
      DealEventSourceEntity representative = src.get(0);
      site = representative.getSite();
      sourceUrl = rawPosts.findById(representative.getRawDealPostId()).map(RawDealPost::getUrl).orElse("");
  }
  ```
  `DealEventSourceRepository`:
  ```java
  List<DealEventSourceEntity> findByDealEventId(Long dealEventId);
  ```
  이 파생 쿼리는 `order by`가 없다 — Spring Data JPA가 `select ... from deal_event_source where deal_event_id = ?`를
  그대로 낸다. PostgreSQL은 `ORDER BY` 없는 조회의 행 순서를 보장하지 않는다(플랜 선택·인덱스 사용·VACUUM 등에
  따라 달라질 수 있다). 그런데 `src.get(0)`이 그 순서에서 첫 행을 "대표"로 골라 `site`·`sourceUrl`을 정한다.

  이게 우연이 아니라 **의미 있는 개념**이라는 근거는 두 곳에서 나온다.
  1. `DealMergePolicy.java:31,66-67` — 병합 시 "대표"를 명시적으로 **firstSeen이 더 이른 쪽**으로 계산한다
     (`DealEvent first = existing.firstSeen().isAfter(incoming.firstSeen()) ? incoming : existing;` 그리고
     `first.site()`, `first.sourceUrl()`을 병합 결과에 싣는다). 즉 도메인은 "대표 원문"을 결정론적 규칙으로
     이미 정의해 두고 있다.
  2. 같은 "여러 원문 중 대표 하나" 문제를 겪는 `GetReviewQueueUseCase.java:71-79`의 네이티브 SQL은
     `order by des.id limit 1`로 **명시적으로 정렬해 고른다** — 이 파일이 바로 옆에서 올바른 패턴을 보여준다.

  `DealEventMapper`만 정렬을 생략했다. DB에서 다시 읽어 재구성하는 매 순간(`IngestDealsUseCase`의 병합 판단
  입력, `GetBenchmarkUseCase`/`GetSignalUseCase`/`FollowUpAlertUseCase`/`ResolveReviewItemUseCase` 등 12개
  유스케이스 전부가 이 메서드를 거친다) "대표 site/sourceUrl"이 실행마다 달라질 수 있다.
- **실패 시나리오**: 한 딜이 뽐뿌·루리웹 두 사이트에서 교차검증(VERIFIED)됐다고 하자. `BenchmarkCalculator.java:155`가
  `d.site()`·`d.sourceUrl()`로 `BenchmarkView.DealRef`(화면에 뜨는 "이 가격의 원문 링크")를 만들고,
  `AlertMessageFormatter.java:40,54`가 `deal.sourceUrl()`을 텔레그램 알림 본문에 그대로 싣는다. 두 소스 행의
  물리적 저장 순서가 힙 배치·병렬 스캔 등으로 바뀌면, 같은 딜을 두 번 조회했을 때(예: 알림 발송 시점과 나중에
  웹에서 다시 볼 때) **서로 다른 사이트의 링크**가 나올 수 있다 — 사람이 "아까 본 링크"와 다른 링크를 보고
  혼란스러워하거나, 알림에 실린 사이트와 실제 딜 상세 화면의 사이트가 어긋난다. 절대 원칙 2("시스템은 근거만
  모아준다")가 전제하는 "이 근거는 안정적이다"가 깨진다.
- **이미 막는 장치 확인**: `core/src/test/java/dev/hogumeter/core/application/GetBenchmarkUseCaseTest.java`의
  `insertCrossVerifiedDeal`(96-112행)이 `ppomppu`·`ruliweb` 두 소스를 저장하는 유일한 테스트 픽스처지만,
  `grep -n ".site()" core/src/test/java/`로 전수 확인한 결과 **`site()`/`sourceUrl()` 값 자체를 단언하는
  테스트가 코드베이스에 하나도 없다**. `DealEventMapper`·`CatalogProjection` 전용 테스트 파일도 없다(리뷰
  지시사항에 이미 명시된 대로 "테스트 전무"). `check-dead-columns.sh`·`check-table-wiring.sh`·
  `check-repository-readers.sh`는 컬럼·테이블·리포지토리 메서드의 존재/호출 여부만 보고 **쿼리 순서 결정성**은
  판정 범위 밖이다. 어떤 장치도 이 비결정성을 막지 않는다.
- **권고**: `DealEventSourceRepository.findByDealEventId`에 `OrderBy`를 추가한다(가장 간단히는
  `findByDealEventIdOrderByIdAsc` — `id`는 `bigserial`이라 삽입 순서와 일치하고, `GetReviewQueueUseCase`가
  이미 같은 규칙을 쓴다). `DealMergePolicy`의 의도(firstSeen이 가장 이른 원문)에 정확히 맞추려면 `deal_event_source`에
  원문의 `firstSeen`을 끌어와 정렬하거나, 최소한 `raw_deal_post.posted_at`/`captured_at`으로 정렬한다. 정렬 기준을
  정했으면 `site()`/`sourceUrl()` 값을 실제로 단언하는 테스트를 `DealEventMapper` 또는 `GetBenchmarkUseCase` 레벨에
  하나 추가해 회귀를 막는다.

### A3-02 — `RawDealPostUpserter`가 Spring 빈으로 배선되지 않아 프로덕션에서 전혀 실행되지 않는다 · Medium
- **위치**:
  - `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPostUpserter.java:1-25`
  - `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPostRepository.java:10`
  - `core/src/test/java/dev/hogumeter/core/adapter/persistence/RawDealPostUpsertTest.java:26-27`
- **근거**: 클래스 선언에 `@Component`·`@Service` 등 스테레오타입 애노테이션이 없다.
  ```java
  public class RawDealPostUpserter {
      private final RawDealPostRepository repository;
      public RawDealPostUpserter(RawDealPostRepository repository) { this.repository = repository; }
      public RawDealPost upsert(...) { ... }
  }
  ```
  `grep -rn "\.upsert(" core/src/main/java/`(리포지토리 전체) 결과 **호출부가 0**이고, 이 클래스를 `new`하는
  곳은 테스트뿐이다:
  ```java
  // RawDealPostUpsertTest.java:26-27
  private RawDealPostUpserter upserter;
  @BeforeEach void setUp() { repository.deleteAll(); upserter = new RawDealPostUpserter(repository); }
  ```
  이 클래스가 유일하게 쓰는 `RawDealPostRepository.findBySiteAndPostId`(`RawDealPostRepository.java:10`)도
  프로덕션 호출자가 이 클래스 하나뿐이라, 사실상 이 클래스 전체가 애플리케이션 컨텍스트에 빈으로 등록되지도,
  어떤 요청 경로에서도 실행되지도 않는다. 그런데 클래스 javadoc은 "멱등 수집 업서트(BM-01 AC-1/AC-2)...
  UNIQUE 제약이 최종 방어선"이라고 적어 **이것이 실제 수집 경로의 멱등성 보증 지점인 것처럼** 서술한다.
  실제 프로덕션 쓰기 경로는 CLAUDE.md가 명시하듯 `collector`(Python)가 `raw_deal_post`에 **직접** 적재하며,
  `grep -rln "raw_deal_post" collector/`로 확인한 `collector/src/collector/db/raw_deal_sink.py`가 그 실제
  업서트 로직을 갖고 있다 — 이 Java 클래스와는 완전히 별개 구현이다.
- **실패 시나리오**: 나중에 누군가 `raw_deal_post` 재수집 시 갱신 로직(예: `origin` 필드도 재적재 시 갱신하게
  하거나, `body_text`를 새로 매핑)을 고치려고 이 클래스가 "그 로직이다"라고 오인하고
  `RawDealPost.refreshFrom()`/`RawDealPostUpserter.upsert()`를 수정한 뒤 `RawDealPostUpsertTest`가
  GREEN인 것을 보고 배포한다. 하지만 프로덕션에서 `raw_deal_post`를 쓰는 유일한 경로는 Python
  `raw_deal_sink.py`이므로 이 수정은 **실제 수집 파이프라인에 아무 영향도 주지 않는다** — 테스트는 초록인데
  운영 동작은 그대로인 상태가 발견되지 않은 채 남는다.
- **이미 막는 장치 확인**: `check-table-wiring.sh`는 `raw_deal_post`라는 테이블명이 프로덕션 코드(Java+Python+web)
  어딘가에 **나타나면** 통과시킨다 — `RawDealPostUpserter.java` 자체가 그 테이블명을 언급하는 프로덕션 파일이라
  이 게이트를 통과한다(테이블 배선 자체는 실제로도 살아 있다 — collector 쪽에서). `check-repository-readers.sh`는
  `*Repository.java`가 선언한 **커스텀 조회 메서드**의 호출자를 프로덕션 소스에서 찾는데, `RawDealPostUpserter.java`가
  `findBySiteAndPostId`를 호출하는 프로덕션 파일로 잡히므로 이 메서드도 "호출자 있음"으로 통과한다 — 그런데
  정작 `RawDealPostUpserter` 자신이 빈으로도 안 등록되고 아무도 안 부르는 계층이라는 사실은 **두 게이트 다
  스코프 밖**이다(리포지토리 메서드 호출자 감사이지, "그 호출자가 Spring 컨테이너에서 실제로 도달 가능한가"는
  보지 않는다). `docs/91-open-questions.md`에서 이 상태를 다루는 열린 항목도 없다(확인: 파일 전체에
  `RawDealPostUpserter` 언급 없음).
- **권고**: 이 클래스가 정말 필요 없다면(collector가 유일한 쓰기 경로라는 현재 계약이 맞다면) 삭제하고
  `RawDealPostUpsertTest`도 함께 제거한다 — Java 쪽 "멱등 업서트"는 테스트로만 사는 문서화된 예시로 남기려는
  의도였다면 javadoc 첫 줄에 "프로덕션 미배선, 계약 문서화용 참조 구현"이라고 명시한다. 반대로 이 클래스가
  실제로 필요해질 예정(예: REST 수집 API 추가)이라면 `@Component`를 붙이고 실제 호출부를 연결한 뒤
  `docs/91`에 재개 트리거를 적는다.

## 검토했으나 문제없음 (근거)
- **엔티티↔DDL 타입 정합 전수**: 24개 엔티티 전부 대응 `V*.sql` 컬럼과 자바 타입·`nullable`·CHECK 허용 enum
  값을 대조했다. `smallint`(`quiet_hours_start/end` → `AlertPolicyEntity.java:38,42` `@JdbcTypeCode(SMALLINT)`),
  `jsonb`(`price_axis_values` → `VariantEntity.java:25`, `demand_axis_filter` → `AlertPolicyEntity.java:68`,
  `payload` → `ReviewQueueItemEntity.java:34`), `text[]`(`applied_conditions`·`product_candidates`·
  `exclude_keywords`·`required_keywords`·`allowed_values`·`keywords` 전부 `@JdbcTypeCode(ARRAY)`) 모두 일치.
  `ddl-auto=validate`(`application.yml:10`)가 컨텍스트 로드마다 이를 강제하므로, 불일치가 있었다면
  `@SpringBootTest` 전체가 무더기로 실패했을 것이다 — 실측 불필요할 만큼 구조적으로 보증된다.
- **DealStatus/OutlierFlag/Origin/PurchaseState/Tier enum ↔ CHECK 제약**: 도메인 enum 상수 집합과
  `V1__init.sql`의 `check (... in (...))` 목록을 각각 대조 — `DealStatus`(NEW/ACTIVE/VERIFIED/ENDED),
  `Origin`(LIVE/BACKFILL), `OutlierFlag`(NONE/UPPER/LOWER), `PurchaseState`(V2에서 OBSERVING/REPORT_PENDING/
  CLOSED/ARCHIVED), `Tier`(SUFFICIENT/SPARSE/NONE, DB엔 snap_tier로 저장) 모두 일치. `deal_alert.kind`는
  V20·V21이 `FollowUpKind`(순수 String, enum 아님) 확장분(REOPENED·PINNED_PRICE_INCREASED)에 맞춰 같은
  커밋에서 CHECK를 갱신한 이력을 확인(`docs/99-lessons` 교훈이 실제로 지켜졌다).
- **V↔R 23쌍 대칭성**: `V1~V23`이 만든 테이블·컬럼·인덱스·제약을 각 `R1~R23`이 역순으로 정확히 되돌리는지
  전부 대조. 데이터 손실 없이 실패하도록 설계된 곳(R15의 `last_successful_poll_at set not null`, R20/R21의
  CHECK 재추가)은 각 파일 주석이 그 전제(개발/드릴 전용 컨텍스트)를 명시하고 있어 의도된 설계다.
- **네이티브 SQL 6곳 전수**(`PreserveAppliedConditionsUseCase`·`GetReviewQueueUseCase`·`ResolveReviewItemUseCase`·
  `GlobalExcludeKeywords`·`IncrementDigestQueueAppearancesUseCase`·`PipelineScheduler`): 전부 `?`/`:param`
  바인딩만 쓰고 사용자 입력을 문자열 연결로 SQL에 넣는 곳이 없다(인젝션 없음). 정렬이 필요한 두 곳
  (`PreserveAppliedConditionsUseCase.java:55`, `GetReviewQueueUseCase.java:91`) 모두 `collate "C"`로 로케일
  의존성을 이미 제거했다(`docs/99-lessons` 2026-07-10 교훈이 실제로 적용됨).
- **`RawDealPostUpserter`의 멱등성 자체(테스트 로직 한정)**: A3-02와 별개로, `upsert()`가 하는 일 자체
  (`findBySiteAndPostId` → 있으면 `refreshFrom`, 없으면 `save(new ...)`)는 (site, post_id) 자연키 기준으로
  올바르게 멱등하고, `RawDealPost.refreshFrom()`(`RawDealPost.java:120-125`)은 `url`·`title`·`capturedAt`·
  `status`만 갱신하고 `origin`은 건드리지 않는데 — 이 클래스가 어차피 프로덕션에서 실행되지 않으므로(A3-02)
  이 부분 갱신 설계 자체의 옳고 그름은 실질적 영향이 없다.
- **delete+insert 패턴**: 전 리포지토리·유스케이스에서 `deleteAll`+`save`류 갱신 패턴을 찾지 못했다(테스트의
  `repository.deleteAll()`은 픽스처 초기화이지 갱신 로직이 아니다). 미매핑 컬럼이 있는 테이블(`deal_event`의
  `shipping`·`base_price`·`confidence`, `raw_deal_post`의 `body_text`·`reaction_score`·`raw`,
  `used_listing_observation`의 `raw`)은 전부 JPA `save()`(UPDATE) 경로만 쓰거나 아예 쓰기 자체가 없어
  (`UsedListingObservationEntity`는 core가 읽기 전용) 미매핑 컬럼이 기본값으로 되돌아갈 경로가 없다.
- **금액 타입**: 가격 관련 컬럼(`price_first/min/max/last`·`paid_price`·`regular_price`·`wow_price`·
  `shipping_fee`·`target_price` 등)은 전부 `bigint`(원 단위 정수) ↔ 자바 `long`/`Long`이다. `double`/`float`
  사용처를 `adapter/persistence` 전체에서 grep했으나 0건.
- **AlertPolicySettings·ExcludeKeywordPolicy의 null 안전성**: `AlertPolicyEntity`의 `exclude_keywords`는
  DB `NOT NULL`인데, 유일한 프로덕션 생성 경로(`AlertPolicySettingsUseCase`)가 거치는
  `AlertPolicySettings`의 컴팩트 생성자가 `ExcludeKeywordPolicy.normalize()`로 `null → List.of()`를
  보장해 NOT NULL 위반 가능성이 실제로는 없다.

## 시간·범위 한계로 못 본 것
- **쿼리 실행계획·인덱스 적정성**은 스키마·리포지토리 메서드 시그니처 대조로만 확인했고 `EXPLAIN`은 돌리지
  않았다. `WatchItemRepository.findByDealEventId`/`findByDealEventIdIn`은 `watch_item`에 있는 유일한 인덱스가
  `uq_watch_item_active_deal(deal_event_id) where state='ACTIVE'`(부분 인덱스)라 `state` 조건이 없는 이
  조회들은 그 인덱스를 못 쓰고 순차 스캔한다 — 다만 프로젝트가 명시적으로 "1인용 규모, 과최적화 금지"(PERF-04)
  방침이라 Low로도 올리지 않았다. `DealEventRepository.findByStatusIn`도 같은 이유로 별도 인덱스 없이 순차
  스캔이지만 마찬가지로 규모상 실질 영향이 없다고 판단해 제외했다.
- **`CatalogProjection.aliasDictionary()`가 `product_id IS NULL`(전역 별칭) 행을 걸러내는 부분**은 코드상
  일관되지만(그런 행이 실제로 만들어지는 프로덕션 경로가 현재 하나도 없음을 확인) "전역 별칭" 기능 자체가
  쓰기·읽기 양쪽 다 미구현 상태라는 사실을 뒷받침할 `docs/91` 열린 항목을 찾지 못했다 — 영속성 계층 결함은
  아니라고 판단해 제외했지만, 기능 완성도 관점의 확인은 다른 리뷰어 몫일 수 있다.
- **collector(Python) 쪽 `raw_deal_sink.py`·`used_listing_sink.py`의 실제 업서트 SQL**은 A3-02의 근거로
  존재만 확인했고(grep), 그 자체의 멱등성·부분갱신 로직은 Python 담당 리뷰어 범위로 보고 상세 대조하지 않았다.
- **런타임 검증**(`./gradlew test`, `scripts/rollback-drill.sh` 등 실제 실행)은 리뷰 지침상 코드 수정·실행이
  금지돼 있어 수행하지 않았다 — 정적 대조로만 결론 내렸다.
