# B1 — core REST 어댑터 (`adapter/web/**`) 리뷰

## 요약 (High N / Medium N / Low N / Info N)
High 0 / Medium 4 / Low 4 / Info 3.

25파일 전부 읽었다. 예외 매핑 표는 완전하다(애플리케이션 예외 15개 + 도메인 예외 5개 + web 패키지 예외 2개 = 22개, 핸들러 메서드 22개 전부 대응, 코드 중복 없음). 실결함은 **"신형 컨트롤러 3곳이 `@RequestBody` record의 null 필드를 그대로 도메인/영속 계층까지 흘려보내 미매핑 예외(NPE·`DataIntegrityViolationException`)로 500이 나는" 같은 패턴의 반복**이다 — `RecordPurchaseCommand.purchasedAt`, `AxesRequest.names`, `NoteRequest.body`, 그리고 `UsedSearchController`/`ComparisonAxisController`의 `productId` 미검증. 이 프로젝트 자체가 이미 세운 관례(`existsById` 선검사, compact-constructor 검증 — `AlertPolicySettings`·`RegisterProductUseCase.validate`가 모범)를 신형 3곳만 못 지켰다. `CoupangObservationController`의 GET/POST 인증 비대칭은 조사 결과 **실결함이 아니다**(판정 근거는 B1-06). `GlobalSettingsController`는 리뷰 지시문의 전제("web 테스트 0건")가 **틀렸다** — 실측 결과는 아래 B1-08.

### B1-01 — `PUT .../comparison-axes`에 `names` 없이 보내면 NPE로 500 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/ComparisonAxisController.java:27`, `core/src/main/java/dev/hogumeter/core/application/DefineComparisonAxesUseCase.java:25-27`
- **근거**:
  ```java
  // ComparisonAxisController
  public List<AxisView> define(@PathVariable long productId, @RequestBody AxesRequest req) {
      return useCase.ensure(productId, req.names())...
  }
  public record AxesRequest(List<String> names) {}
  ```
  ```java
  // DefineComparisonAxesUseCase
  public List<ComparisonAxisEntity> ensure(long productId, List<String> names) {
      for (String name : names) { ... }   // names == null이면 NPE
  ```
  Jackson(record 생성자 바인딩)은 JSON에 없는 참조타입 필드를 `null`로 채운다. `AxesRequest`엔 방어 코드가 없고 `ensure()`도 null 체크가 없다.
- **실패 시나리오**: `PUT /api/v1/products/{productId}/comparison-axes` 바디 `{}` (`names` 누락) → `for (String name : names)`에서 `NullPointerException` → `ApiExceptionHandler`에 미매핑 → Spring 기본 500(`{code, message}` 계약 붕괴, `web/src/api/client.ts`는 `HTTP_500`으로만 표시).
- **이미 막는 장치 확인**: 클래스명 Grep(`ComparisonAxisController`) 결과 테스트 없음. 경로 문자열 Grep(`/comparison-axes`) 결과 `core/src/test/java/dev/hogumeter/core/adapter/web/UsedComparisonEndpointsTest.java`가 유일하고, 그 파일의 두 케이스(`noteDefineAxisPromoteAndCompareEndToEnd`, `redefiningAxesIsAdditiveNotDestructive`) 모두 `"names"` 필드를 채워 보낸다 — 누락 케이스 없음. **없음**.
- **권고**: `AxesRequest` 컴팩트 생성자나 `ensure()` 시작부에 `names == null`이면 빈 목록으로 정규화하거나 `InvalidRegistrationException` 계열로 400 거절. `UsedSearchController.orEmpty(...)`가 이미 같은 문제를 해결한 패턴이니 그대로 재사용 가능.

### B1-02 — `POST /purchases`에 `purchasedAt` 없이 보내면 NPE로 500 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseUseCase.java:84-88`
- **근거**:
  ```java
  Optional<Instant> observedFrom = deals.stream().map(DealEvent::firstSeen).min(Instant::compareTo);
  if (observedFrom.isPresent() && cmd.purchasedAt().isBefore(observedFrom.get())) {
      return Snapshot.unobserved(basis);
  }
  Clock asOf = Clock.fixed(cmd.purchasedAt(), clock.getZone()); // purchasedAt이 null이면 NPE
  ```
  `RecordPurchaseCommand.purchasedAt`은 `Instant`(참조타입) — 요청 바디에서 빠지면 `null`. 표본 딜이 하나라도 있으면 `cmd.purchasedAt().isBefore(...)`에서, 표본이 아예 없어도 그 아래 `Clock.fixed(null, zone)`(내부에서 `Objects.requireNonNull`)에서 예외 없이는 못 지나간다 — **양쪽 경로 모두 NPE**.
- **실패 시나리오**: `POST /api/v1/purchases` 바디 `{"variantId":1,"demandAxisValue":"256GB","paidPrice":940000}` (`purchasedAt` 누락) → `NullPointerException` → 미매핑 → 500. `@Transactional`이라 롤백은 되어 데이터 손상은 없지만, 사용자에게 "purchasedAt이 필요합니다" 같은 답을 못 준다.
- **이미 막는 장치 확인**: 클래스명 Grep(`PurchaseController`) → `PurchaseControllerTest`(슬라이스)·`PurchaseEndpointTest`(`@SpringBootTest`) 존재. 경로 문자열 Grep(`/api/v1/purchases`) 결과 두 파일 다 확인 — `PurchaseEndpointTest.java`의 4개 테스트 모두 `purchasedAt`을 명시적으로 채운다(`body(paidPrice, purchasedAt)` 헬퍼가 항상 두 값을 다 받음). 누락 케이스 없음. **없음**.
- **권고**: `RecordPurchaseCommand` 컴팩트 생성자 또는 `RecordPurchaseUseCase.record()` 진입부에서 `purchasedAt == null` → `InvalidRegistrationException` 계열(또는 전용 예외)로 400. `paidPrice`(B1-05)와 같이 처리하면 한 번에 정리된다.

### B1-03 — `productId` 미검증인 두 컨트롤러가 FK 위반을 그대로 500으로 흘린다 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RegisterUsedSearchUseCase.java:29-35`, `core/src/main/java/dev/hogumeter/core/application/DefineComparisonAxesUseCase.java:24-31`
- **근거**: `used_search.product_id`·`comparison_axis.product_id` 둘 다 `references product (id)`(`core/src/main/resources/db/migration/V3__used.sql:9, 71`)로 FK가 걸려 있는데, 두 유스케이스 모두 `RegisterProductUseCase.validate`나 `GetBenchmarkUseCase`·`RecordPurchaseUseCase`가 쓰는 `products.existsById(...)` 선검사가 없다:
  ```java
  // RegisterUsedSearchUseCase.register — productId 존재 확인 없이 바로 save
  UsedSearchEntity search = searches.save(new UsedSearchEntity(cmd.productId(), "BUNJANG", ...));
  ```
  ```java
  // DefineComparisonAxesUseCase.ensure — 동일
  axes.save(new ComparisonAxisEntity(productId, name));
  ```
- **실패 시나리오**: `POST /api/v1/products/999999/used-searches`(존재하지 않는 productId) 바디는 정상 → INSERT가 FK 제약 위반 → `DataIntegrityViolationException` → `ApiExceptionHandler` 미매핑 → 500 (`{code,message}` 계약 대신 Spring 기본 오류 본문). `PUT /api/v1/products/999999/comparison-axes`도 동일.
- **이미 막는 장치 확인**: 클래스명 Grep(`UsedSearchControllerTest`) — `postCreatesUsedSearchAndReturnsId`·`getListsRegisteredSearchesAndEmptyForUnknownProduct` 존재하나 후자는 **GET**만 미존재 productId(999999)를 시험하고(빈 배열 200, 문제없음) POST는 시험 안 함. `ComparisonAxisController`도 동일 파일(`UsedComparisonEndpointsTest`)에 미존재 productId POST/PUT 테스트 없음. **없음**.
- **권고**: 두 유스케이스 시작부에 `if (!products.existsById(productId)) throw new ProductNotFoundException(productId);` 추가 — 이미 있는 `ProductNotFoundException`(404, `PRI_PRODUCT_NOT_FOUND`)을 그대로 재사용하면 새 코드도 필요 없다.

### B1-04 — `ApiExceptionHandler`에 미매핑 예외 catch-all이 없어 `{code,message}` 계약이 케이스별로 깨진다 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/ApiExceptionHandler.java` (166줄 전체 — `@ExceptionHandler(Exception.class)`류 없음)
- **근거**: 이 파일이 프로젝트의 유일한 `@RestControllerAdvice`다(Grep 확인, 다른 곳에 없음). B1-01/02/03이 보여주듯 `NullPointerException`·`DataIntegrityViolationException`은 여기 안 걸리고 Spring Boot 기본 오류 응답(`{"timestamp","status","error","path"}`, `message`/`trace` 기본 비노출이라 내부정보 유출은 없음)으로 떨어진다. `web-react.md`의 "core가 `{code,message}`를 주면 code를 보존하고, 아니면 `HTTP_{status}`로 살린다"는 이 결손을 클라이언트가 이미 흡수하도록 설계돼 있어 화면이 깨지진 않지만, **사용자는 "purchasedAt 빠짐"과 "DB 자체 오류"를 구분 못 하고 둘 다 `HTTP_500`으로 본다** — 절대 원칙 6(과대약속 금지)과 반대로 "무엇이 왜 실패했는지"를 숨기는 쪽으로 작동한다.
- **실패 시나리오**: 위 B1-01~03 세 가지 전부.
- **이미 막는 장치 확인**: `ApiExceptionHandler`에 대한 전용 테스트 파일 자체가 없다(파일명 Grep `ApiExceptionHandlerTest` 0건). 개별 매핑은 각 컨트롤러 테스트가 간접 검증하지만 **미매핑 경로**를 시험하는 테스트는 프로젝트 전체에 하나도 없다. **없음**.
- **권고**: `@ExceptionHandler({DataIntegrityViolationException.class, NullPointerException.class})` 같은 최종 방어선을 추가해 최소 "요청 처리 실패" 계열 400/500 + 안전한 `{code,message}`를 보장하되, 근본 수정은 B1-01/02/03처럼 발생 지점에서 400으로 막는 것이 우선이다(회귀는 놓치고 후속 유사 결함만 다시 500으로 새는 걸 막는 안전망 정도로 두라).

### B1-05 — `RecordPurchaseCommand.paidPrice`가 음수/0이어도 그대로 저장된다 · Low
- **위치**: `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseUseCase.java:57-73`, `core/src/main/java/dev/hogumeter/core/application/RecordPurchaseCommand.java:16`
- **근거**: `record()` 전체에 `cmd.paidPrice()`에 대한 범위 검증이 없다. `InvalidCoupangObservationException`(`가격은 0보다 커야 합니다`, `IngestCoupangObservationUseCase.java:29`)·`AlertPolicySettings`(`targetPrice must be positive when set`)가 이미 같은 종류의 검증을 이 코드베이스에서 관례로 삼고 있는데 구매 기록만 빠졌다.
- **실패 시나리오**: `POST /api/v1/purchases` 바디의 `paidPrice: -1`(또는 `0`) → 정상 201 저장 → `PurchaseObservation`·성적표(`snapPaidGap` 등)가 말이 안 되는 값을 영구적으로 담는다(구매 기록은 수정 API가 없어 되돌리기 어렵다). 화면·알림이 "얼마에 사서 얼마나 손해/이득"을 잘못 계산해 정직성 원칙(절대 원칙 1)을 조용히 어긴다.
- **이미 막는 장치 확인**: `PurchaseEndpointTest`·`PurchaseControllerTest` 전부 양수 가격만 사용. `RecordPurchaseCommand`/`Purchase` 어디에도 컴팩트 생성자 검증 없음. **없음**.
- **권고**: `RecordPurchaseUseCase.record()` 진입부에 `if (cmd.paidPrice() <= 0) throw new InvalidRegistrationException(...)` 류 추가(B1-02의 `purchasedAt null` 검사와 함께 정리하면 한 번의 변경).

### B1-06 — `CoupangObservationController` GET/POST 인증 비대칭 · Info (실결함 아님, 판정 결과)
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/CoupangObservationController.java:52-72`
- **근거**: `ingest()`(POST)는 `authenticate(token)`을 부르고 `latest()`(GET, 66-72줄)는 안 부른다.
- **판정**: 이 프로젝트의 REST 표면 25개 엔드포인트 전체를 확인한 결과, **GET 계열은 예외 없이 애플리케이션 레벨 인증이 전혀 없다**(`ProductQueryController`·`BenchmarkController`·`SignalController`·`WatchController.active()` 등 전부 동일) — 이 프로젝트는 앱 레벨 인증을 아예 두지 않고 nginx Basic Auth(및 Caddy 프로파일)라는 **경계 한 겹**으로 전체를 막는 설계다(프로젝트 배경 명시, `web-react.md`의 "인증은 nginx에만 있으면 core 포트가 0.0.0.0에 열렸는지도 함께 본다"도 같은 전제). `latest-price`만 대상으로 앱 레벨 인증을 추가로 요구할 근거는 코드 안에 없다 — 반대로 POST에 토큰을 요구하는 이유는 SEC-04 주석이 명시하듯 "확장이 사용자 브라우저에서 읽어 보낸 값을 **저장**(쓰기)하기 전 최소 검증"이지 조회 자체를 막으려는 설계가 아니다. CORS 설정도 없어(Grep 0건) 브라우저 간 교차 오리진 읽기는 Same-Origin Policy로 별도 차단된다.
- **결론**: 이 비대칭은 **의도된 것으로 판단**한다(테스트 `noObservationYetReturnsAllNullsNotFabricatedZeros`가 토큰 없는 GET 200을 이미 명시적으로 검증). High/Medium으로 올리지 않는다 — nginx Basic Auth가 실제로 꺼져 있는지는 배포 설정 문제(`scripts/preflight.sh` 영역)이지 이 컨트롤러의 결함이 아니다.
- **참고(부수 발견)**: 같은 파일에서 **429(`RateLimitExceededException`) 분기 자체엔 MockMvc 테스트가 없다** — `CoupangObservationControllerTest`(경로 문자열 `/observations` Grep으로 확인)의 5개 테스트 모두 요청 1회씩만 보내 레이트리밋 창을 채우지 않는다. 순수 도메인(`FixedWindowRateLimiterTest`)만 있고 컨트롤러 배선(429 상태코드 + `RATE_LIMIT_EXCEEDED` 코드가 실제로 나가는지)은 미검증. Low로 기록.

### B1-07 — `GlobalSettingsController` REST 계약에 HTTP 레벨 테스트가 없다(단, 지시문 전제는 틀렸다) · Low
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/web/GlobalSettingsController.java`
- **근거/정정**: 지시문은 "전체 web 테스트에서 `/api/v1/settings` 경로 참조가 0건"이라 했으나, 실측 결과 **틀렸다** — `web/src/settings/SettingsPage.test.tsx`(5개 케이스)가 `SettingsPage`를 렌더링해 저장·조회·에러 표시를 검증하고, `web/src/api/client.ts:159-162`가 실제로 `/api/v1/settings/exclude-keywords`를 호출한다(단, 테스트는 `vi.spyOn(api, 'getGlobalExcludeKeywords')`로 API 모듈 자체를 모킹해 리터럴 URL 문자열을 직접 참조하진 않는다 — Grep으로 URL 문자열만 찾으면 놓친다).
- **남는 진짜 갭**: **core 쪽**엔 `GlobalSettingsController`에 대한 MockMvc/`@SpringBootTest` 테스트가 없다. 서비스 계층(`GlobalExcludeKeywordsTest`, 클래스명 Grep으로 확인)만 있고, `GET/PUT /api/v1/settings/exclude-keywords`의 경로·JSON 바인딩·상태코드를 관통하는 테스트는 core 테스트 트리 전체에 0건(경로 문자열 `/api/v1/settings` Grep 결과 `working-area/progress-log.md`·`docs/91`·`web/*`·`scripts/smoke.sh`·컨트롤러 자신뿐, `core/src/test/**`엔 없음).
- **실패 시나리오**: 컨트롤러 로직 자체는 단순해 지금 당장 깨진 것은 없다(코드 리뷰로 확인, 로직 문제없음 — 아래 "검토했으나 문제없음" 참고). 다만 경로 오타·HTTP 메서드 실수·JSON 바인딩 회귀가 나면 core 테스트 스위트가 못 잡고 `scripts/smoke.sh`(경로 문자열만 언급, 실제 검증 단계인지는 미확인) 또는 사람이 화면에서 발견해야 한다.
- **권고**: `GlobalSettingsControllerTest`(`@SpringBootTest`+MockMvc) 하나 추가 — GET 기본값(빈 목록)·PUT 정규화 왕복·빈 배열 저장 세 케이스면 충분.

### B1-08 — 목록류 GET 엔드포인트가 상한 없이 전체를 반환한다 · Low
- **위치**: `ProductQueryController.java:20`(`products()`), `PriorityController.java:33`(`prioritized()`), `ReviewQueueController.java:34`(`pending()`), `WatchController.java:36,42`(`active()`,`resolved()`)
- **근거**: 전부 `List<...>`를 페이지네이션 파라미터 없이 그대로 반환한다.
- **판정**: 1인용·자기 등록 제품 수 규모(수십~수백 건)를 고려하면 지금 시점엔 실사용 리스크가 낮다. High/Medium으로 올리지 않는다 — 다만 딜 재현율 우선 원칙상 미상 큐(`review-queue`)나 `resolved` 회고 탭이 시간이 지나며 무한정 쌓이는 종류라 향후 성능 이슈 후보로만 남긴다.
- **권고**: 지금 당장 조치 불필요. 항목 수가 실제로 체감 지연을 만들면 그때 `LIMIT`/커서 페이지네이션 도입.

## 예외 매핑 대조표
| 애플리케이션 예외 | ApiExceptionHandler 처리 | HTTP 상태 | 에러코드 |
|---|---|---|---|
| `ComparisonAxisNotFoundException` | 있음 | 404 | `COMPARISON_AXIS_NOT_FOUND` |
| `DealAlreadyPinnedException` | 있음 | 409 | `WATCH_ALREADY_PINNED` |
| `DealEventNotFoundException` | 있음 | 404 | `WATCH_DEAL_NOT_FOUND` |
| `DealNotPinnableException` | 있음 | 400 | `WATCH_DEAL_NOT_PINNABLE` |
| `DemandAxisValueRequiredException` | 있음 | 400 | `BM_DEMAND_AXIS_VALUE_REQUIRED` |
| `DuplicatePriorityRankException` | 있음 | 409 | `PRI_DUPLICATE_RANK` |
| `InvalidCoupangObservationException` | 있음 | 400 | `INVALID_COUPANG_OBSERVATION` |
| `InvalidRegistrationException` | 있음 | 400 | `REG_INVALID_PRODUCT` |
| `ListingNotFoundException` | 있음 | 404 | `LISTING_NOT_FOUND` |
| `ProductNotFoundException` | 있음 | 404 | `PRI_PRODUCT_NOT_FOUND` |
| `PurchaseNotFoundException` | 있음 | 404 | `PURCHASE_NOT_FOUND` |
| `ReviewItemNotFoundException` | 있음 | 404 | `REVIEW_ITEM_NOT_FOUND` |
| `UnclassifiedPromoteNotSupportedException` | 있음 | 400 | `REVIEW_PROMOTE_UNSUPPORTED` |
| `UsedSearchNotFoundException` | 있음 | 404 | `USED_SEARCH_NOT_FOUND` |
| `WatchItemNotFoundException` | 있음 | 404 | `WATCH_ITEM_NOT_FOUND` |
| `InvalidAlertPolicyException`(domain.alert) | 있음 | 400 | `REG_INVALID_ALERT_POLICY` |
| `InvalidBenchmarkPeriodException`(domain.benchmark) | 있음 | 400 | `BM_INVALID_PERIOD`(REG-03과 재사용, 중복 아님) |
| `VariantNotFoundException`(domain.benchmark) | 있음 | 404 | `BM_VARIANT_NOT_FOUND` |
| `IllegalPurchaseTransitionException`(domain.purchase) | 있음 | 409 | `PUR_ILLEGAL_TRANSITION` |
| `IllegalPinTransitionException`(domain.watch) | 있음 | 409 | `WATCH_ILLEGAL_PIN_TRANSITION` |
| `ExtensionAuthException`(adapter.web) | 있음 | 401 | `EXTENSION_AUTH_FAILED` |
| `RateLimitExceededException`(adapter.web) | 있음 | 429 | `RATE_LIMIT_EXCEEDED` |
| *(미매핑)* `NullPointerException` / `DataIntegrityViolationException` / `HttpMessageNotReadableException` / `MethodArgumentTypeMismatchException` 등 | **없음(500 또는 Spring 기본값으로 샘)** — 앞의 둘은 B1-01/02/03/04가 500으로 새는 경로를 지목. 뒤의 둘은 Spring MVC가 자체적으로 400 처리(HttpMessageNotReadableException·MethodArgumentTypeMismatchException)해 실질 문제는 없음, 참고로만 기재 | 가변 | 없음 |

15개 애플리케이션 예외 전부 처리됨을 확인. 코드 문자열 중복 없음(`CODE` 상수 22개 전수 대조, 유일). `BM_INVALID_PERIOD`는 `InvalidBenchmarkPeriodException` 하나가 REG-03(`AlertPolicySettings`)·BM-06(`BenchmarkCalculator`) 양쪽에서 재사용되고 있어 "에러코드는 개념 단위로 재사용" 규칙을 잘 지킨 사례다(중복이 아니라 의도된 공유).

## 검토했으나 문제없음 (근거)
- **`HealthController`/`HealthReport`**: 예외 메시지 대신 타입 이름만 노출(`Component.down`), 빈 컴포넌트 집합은 `IllegalArgumentException`으로 방어, DB 프로브 타임아웃 2초로 compose 3초보다 짧음. `HealthControllerTest`(IO 없는 판정 테스트 3개)·`HealthEndpointTest`(`@SpringBootTest` 실 DB 2개)·`HealthReportTest`가 이 세 특성을 각각 관통 검증. 결함 없음.
- **`GlobalSettingsController`/`GlobalExcludeKeywords`**: null `excludeKeywords` 요청 바디도 `ExcludeKeywordPolicy.normalize(null) → List.of()`로 안전 처리(NPE 없음). 손상된 JSON 저장값도 `catch (RuntimeException) → List.of()`로 조회가 죽지 않는다. sentinel 없음(부재=빈 목록, 일관).
- **`AlertPolicyController`/`AlertPolicySettings`**: `targetPrice`(0 이하 거절)·`periodMonths`(0 이하 거절)·`kDisplay`(3~10 범위)·`quietHours`(0~23, 한쪽만 설정 거절) 전부 compact constructor에서 400으로 막힘 — 이 프로젝트의 검증 모범 사례. `UpdateRequest`가 전 필드를 박싱 타입(`Integer`/`Long`)으로 받아 언박싱 NPE도 없음.
- **`CoupangObservationController` 가격 검증**: `regularPrice <= 0` → `INVALID_COUPANG_OBSERVATION` 400(`IngestCoupangObservationUseCase.java:29`), 토큰 상수시간 비교(`MessageDigest.isEqual`), 미설정 토큰은 전부 거절(빈 값 우회 없음) — `CoupangObservationControllerTest` 5개 케이스가 이 전부를 관통 검증.
- **`BenchmarkController`/`CadenceController`/`SignalController`**: `periodMonths` 음수·0은 `BenchmarkCalculator`가 `InvalidBenchmarkPeriodException`으로 400(코드 직접 확인). `demandAxisValue` 누락 시 SPLIT 제품은 `DemandAxisValueRequiredException` 400.
- **`ProductQueryController`/`UsedSearchController` GET**: 없는 productId는 404가 아니라 빈 목록 — 문서화된 의도적 계약이고("이 제품의 variant 집합은 공집합일 수 있다"), 대칭적으로 두 컨트롤러가 같은 규칙을 쓴다. sentinel 아님.
- **`ComparisonController`/`RowView`**: 승격 안 된 축 키를 아예 안 내(값 자체를 안 만듦) "미확인"과 "빈 값"을 혼동하지 않음 — sentinel 회피 규칙 준수.
- **CORS**: 설정 자체가 없음(Grep 0건) — `web-react.md`가 명시한 "CORS를 쓰지 않는다"(같은 오리진 전제)와 일치, 결함 아님.

## 시간·범위 한계로 못 본 것
- `AlertStatusController`·`PurchaseObservationController`·`SignalController`·`CadenceController`·`ComparisonController`·`ProductQueryController`·`ReviewQueueController`·`UsedEvaluationController`·`PriorityController`·`WatchController`는 코드를 전부 읽고 명백한 실패 시나리오를 찾지 못했으나, **각 use-case 내부의 도메인 로직 정확성**(예: `EvaluateListingUseCase`의 위험신호 판정, `GetPrioritizedProductsUseCase`의 정렬 기준)까지는 검증하지 않았다 — 그건 A2(usecase) 리뷰 범위로 판단해 재검토하지 않았다.
- `ApiExceptionHandler`가 다루는 도메인 예외 5개(`InvalidAlertPolicyException` 등)의 **발생 조건 자체**가 올바른지(예: `IllegalPurchaseTransitionException`이 실제로 부적절한 상태 전이를 다 잡는지)는 domain 계층 리뷰(A1) 몫으로 남겼다 — 여기선 매핑(예외→HTTP상태→코드)만 검증했다.
- `EvaluationRequest.price`(Long, nullable)·`EvaluationKind`별 필수 필드 조합의 서버측 검증 유무는 `EvaluateListingUseCase` 내부까지 못 열어봤다 — `UsedEvaluationControllerTest`만 훑었고 use-case 본문은 A2 영역이라 판단해 생략.
- 요청 바디 크기 상한(거대 문자열 DoS류)은 Spring Boot 기본 설정(`server.tomcat.max-http-form-post-size` 등) 확인을 안 했다 — 이 프로젝트가 1인용·사설망 전제라 우선순위를 낮게 봤다.
- `scripts/smoke.sh`가 `GlobalSettingsController` 경로를 실제로 호출/검증하는지는 문자열만 확인했고 스크립트 본문의 검증 강도(상태코드만 보는지, 값까지 보는지)는 안 열어봤다.
