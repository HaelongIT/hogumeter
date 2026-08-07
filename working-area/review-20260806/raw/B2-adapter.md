# B2 — 텔레그램 어댑터 + 스케줄러 리뷰

## 요약 (High 1 / Medium 0 / Low 2 / Info 1)
`HttpTelegramApi`(테스트 전무)의 인바운드 경로(`getUpdates`·`answerCallbackQuery`·`editMessageText`)가 HTTP 상태
코드를 전혀 검사하지 않아, 봇 토큰이 무효화되거나 봇이 차단되면 텔레그램 버튼(승격/기각/무시) 채널 전체가 아무
로그·알림 없이 영원히 멈춘다 — 이 리뷰에서 발견한 유일한 High다. `PipelineScheduler`·`PipelineTickReport`·
`AlertMessageFormatter`·`DigestFormatter`·`TelegramAlertSender`의 SEC-08 상태 분류·단계 격리·정직성 원칙은
이미 폭넓은 테스트로 잘 지켜지고 있었다(`PipelineSchedulerTest` 20여 개, `PipelineTickReportTest` 다수 등).

### B2-01 — `HttpTelegramApi` 인바운드 경로가 HTTP 상태를 전혀 검사하지 않아 실패가 완전히 침묵한다 · High
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/telegram/HttpTelegramApi.java:89-100`(`getUpdates`), `:102-109`(`answerCallbackQuery`), `:111-117`(`editMessageText`)
- **근거**:
  ```java
  // getUpdates (line 89-92)
  try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parseCallbacks(response.body());
  }
  ```
  `sendMessage`가 쓰는 `post()`는 상태 코드를 리턴해 `TelegramAlertSender.classify()`가 OK/일시장애/거절로 가르지만(SEC-08),
  `getUpdates`는 `response.statusCode()`를 아예 읽지 않고 바로 `response.body()`를 파싱한다. 텔레그램이 401(토큰 무효)·403(봇 차단)로
  응답해도 본문은 `{"ok":false,...}` 형태라 `result` 키가 없고, `parseCallbacks`(:127)는
  ```java
  if (!(root instanceof Map<?, ?> map) || !(map.get("result") instanceof List<?> updates)) {
      return out; // 빈 목록
  }
  ```
  로 조용히 빈 목록을 반환한다. `TelegramInboundPoller.poll()`은 `RuntimeException`만 잡으므로(HTTP 에러는 예외를 던지지
  않는다) 이 경로는 예외로도 걸리지 않는다. `answerCallbackQuery`(:108 `post(...)`)·`editMessageText`(:116 `post(...)`)도
  `post()`가 돌려주는 상태 코드를 버린다(`void` 반환, 값 미사용) — 두 메서드 모두 실패해도 로그 한 줄 안 남는다.
- **실패 시나리오**: 봇 토큰이 무효화되거나(재발급·유출로 회수) 사용자가 봇을 차단하면, `TelegramInboundPoller`는
  3초마다 계속 `getUpdates`를 호출하지만 매번 401/403을 조용히 빈 목록으로 해석한다. 승격·기각·무시 버튼 콜백을
  처리하는 전체 인바운드 채널이 영구히 멈추는데도, 로그·관리 알림(`AdminNotifier`) 어디에도 흔적이 없다 —
  `PipelineScheduler`처럼 `stepsFailed`를 세는 장치도 이 폴러에는 없다. 운영자는 "버튼을 눌렀는데 반응이 없다"는
  것을 직접 겪기 전까지 알 방법이 없다.
- **이미 막는 장치 확인**: `HttpTelegramApi`는 테스트 전무(과제 명세와 일치, `core/src/test/java/.../adapter/telegram/`
  전체를 확인함 — `HttpTelegramApiTest`류 파일 없음). `TelegramInboundPollerTest`는 `TelegramInboundApi`를 직접 구현한
  `FakeInbound`로 `HttpTelegramApi`를 완전히 우회하므로 이 상태-코드 무시 버그를 잡지 못한다.
- **권고**: `getUpdates`도 `sendMessage`의 `post()`처럼 상태 코드를 먼저 확인해 2xx가 아니면 `TelegramTransportException`
  (또는 별도의 "거절" 예외)을 던지도록 한다 — 토큰 문자열은 여전히 예외 메시지에 담지 않는다(SEC-01 유지).
  `answerCallbackQuery`·`editMessageText`도 최소한 실패 시 `log.warn`으로 상태 코드를 남긴다. `TelegramInboundPoller`
  쪽에서 연속 실패를 세어 `AdminNotifier`로 알리면(파이프라인의 `stepFailures`/`healthTick`과 같은 패턴) 침묵을 없앨 수 있다.

## 검토했으나 문제없음 (근거)
- **SEC-01 토큰 유출**: `HttpTelegramApi.TelegramTransportException`(:156-159)은 `cause`를 체인하지 않고
  `"telegram transport failure: " + cause.getClass().getSimpleName()`만 메시지로 쓴다 — 토큰이 든 URL이 담긴
  원본 예외(`IOException` 등)의 메시지·스택트레이스가 상위 로그에 노출되지 않는다. 의도된 설계(주석과 일치).
- **HTML/Markdown 이스케이프**: `HttpTelegramApi.post()`(:44-60)는 `sendMessage` 요청에 `parse_mode`를 전혀 넣지 않는다
  (form body에 `chat_id`·`text`·`reply_markup`만). 텔레그램은 `parse_mode` 미지정 시 본문을 순수 텍스트로 다루므로
  `AlertMessageFormatter`·`UsedAlertMessageFormatter`·`DigestFormatter`가 만드는 문구의 `<`, `&`, `_`, `*` 등을
  이스케이프할 필요 자체가 없다 — 리뷰 브리핑이 우려한 시나리오가 애초에 발생하지 않는다.
- **메시지 길이 4096자 상한**: 다이제스트는 `SendDigestUseCase.TELEGRAM_MAX_LENGTH=4096` + `DigestSplitter.split()`로
  이미 분할·연번 처리된다(`SendDigestUseCase.java:42-43,81`). 개별 딜/중고 알림은 필드 수가 적어 상한을 넘길 현실적
  경로가 없다.
- **SEC-08 상태 분류**: `TelegramAlertSender.classify()`(:124-132)의 2xx=OK/5xx=TRANSIENT/그 외=REJECTED 분류가
  `TelegramAlertSenderTest.classifiesStatusPerSec08`(200/201/429/403/400/500/503 파라미터화 테스트)로 정확히 잠겨 있다.
  전송 실패(거절·일시장애·transport 예외) 어느 경우에도 `send`/`sendUsed`가 던지지 않음이
  `neverThrowsOnAnyErrorStatus`·`networkTransportFailureDoesNotThrow`로 검증됨.
- **정직성(n건/m건)**: `AlertMessageFormatter.comparisonLine`(:96-119)은 `Tier.SUFFICIENT`이고 `benchmarkPrice`가
  null이 아닐 때만 금액을 내고, SPARSE면 `"표본 N건뿐이라 기준가 대신 참고"`로 대체한다 —
  `sparseFirstAlertClaimsNoBenchmarkAmount` 테스트가 지어낸 기준가 금액이 없음을 정규식으로 잠근다.
- **스케줄러 등록·`fixedDelay` 즉시실행 회피**: `PipelineScheduler`·`TelegramInboundPoller`·`DigestScheduler` 모두
  `initialDelay`(또는 cron)로 기동 즉시 실행을 피하고, `@EnableScheduling`(`SchedulingConfig`) 등록 사실이
  `PipelineSchedulerWiringTest`·`DigestSchedulerWiringTest`로 `ScheduledTaskHolder`를 직접 확인해 검증된다
  (애노테이션 존재가 아니라 등록 사실).
- **파이프라인 단계 순서·격리·카운터**: `PipelineScheduler.tick()`의 순서(만료→성적표발급→ingest→조건태그→핀미리보기→
  가격→종료→핀자동화→후속알림→플러시→중고접기)와 `runStep`/`runStepReturning`의 예외 격리, 스냅샷 조회 실패 시
  "0으로 채운 리포트를 내지 않고 아예 안 낸다"는 계약이 `PipelineSchedulerTest`(20여 개 테스트, 순서·격리·실패
  카운트·`purchasesExpired` 재구성 등)로 촘촘히 검증됨. `PipelineTickReport`의 0-생략 금지, `merged`/`pending` 유도
  산술도 `PipelineTickReportTest`로 잠김.
- **SEC-03 인바운드 화이트리스트**: `ReviewCallbackRouter`(application 계층, 참고로 확인)는 `allowed-chat-ids`와
  `chat-id`가 둘 다 비면 `Set.of()`(닫힌 기본값)로 폴백한다(:37) — "열려면 명시해야 한다"는 원칙과 일치.
- **콜백 재생 시 부작용**: 프로세스 재시작으로 `TelegramInboundPoller.offset`(인메모리, 영속화 안 됨)이 초기화돼도,
  텔레그램 서버 측에서는 이전 프로세스가 확인한 offset 이전 업데이트를 이미 정리했으므로 "봇 기동 이후 전체 재생"은
  일어나지 않는다. 남는 위험은 "처리 직후·offset 확인 전 크래시" 좁은 창인데, `promote`/`reject`는
  `ReviewItemNotFoundException`으로, `ignore`는 `IgnoreDealUseCase.ignore()`의 `ignores.existsByDealEventId` 멱등
  가드(:60-62)로 재생에 안전하다.
- **네이버 스텁**: `StubCurrentPriceProvider.currentPriceFor`는 `0`이 아니라 `null`을 반환해 "갭 -100%" 같은 거짓
  신호를 막는다(Q-53 교훈과 일치).

### B2-02 — `getUpdates`가 콜백이 아닌 업데이트를 로컬에서 완전히 건너뛰어 offset이 정체될 수 있다 · Low
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/telegram/HttpTelegramApi.java:130-133`
- **근거**:
  ```java
  if (!(item instanceof Map<?, ?> update) || !(update.get("callback_query") instanceof Map<?, ?> cq)) {
      continue; // 콜백이 아닌 업데이트는 무시
  }
  ```
  콜백이 아닌 업데이트(예: 봇에게 보낸 일반 텍스트·`/start` 등)는 `CallbackUpdate`로 만들어지지 않아 목록에 아예
  안 실린다. `TelegramInboundPoller.poll()`(:63)은 `api.getUpdates(offset)`이 돌려준 목록만 순회하며 offset을
  전진시키므로, 그 업데이트의 `update_id`는 로컬 offset에 절대 반영되지 않는다.
- **실패 시나리오**: 사용자가 실수로 봇에게 일반 메시지를 보내면, 그 update_id는 다음 콜백이 도착하기 전까지
  매 3초 폴마다 텔레그램에서 계속 재조회된다(불필요한 네트워크 호출만 반복, 부작용은 없음 — 콜백이 아니므로 매번
  다시 필터링됨). 이후 콜백이 하나라도 오면 그 콜백의 update_id가 더 크므로 offset이 그 시점에 함께 전진해
  자연 해소된다. 콜백이 영영 안 오면 무한히 반복된다(사이드이펙트 없는 낭비 트래픽).
- **이미 막는 장치 확인**: 없음 — `TelegramInboundPollerTest`는 `TelegramInboundApi`를 직접 구현한 fake라 이
  `HttpTelegramApi`의 파싱 단계 자체를 거치지 않는다.
- **권고**: 콜백 여부와 무관하게 응답에 있는 모든 update의 최대 `update_id`를 별도로 계산해 offset을 그 값+1로
  전진시킨다(콜백 목록과는 별개 값으로).

### B2-03 — `DigestScheduler.tick()`에 예외 격리가 없다 · Low
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/scheduler/DigestScheduler.java:37-41`
- **근거**:
  ```java
  @Scheduled(cron = "0 0 20 * * SUN", zone = "Asia/Seoul")
  public void tick() {
      DigestSendReport report = sendDigest.send();
      log.info("[DIGEST] parts={} sent={} allSucceeded={}", ...);
  }
  ```
  `PipelineScheduler`는 각 단계를 `runStep`/`runStepReturning`으로 감싸 실패를 격리하고 `stepsFailed`로 세지만,
  `DigestScheduler`는 `sendDigest.send()`가 던지는 `RuntimeException`(예: `assemble()`·`render()` 중 DB 예외)을
  전혀 잡지 않는다. Spring 기본 `LoggingErrorHandler`가 스케줄러 스레드 로거로 로그는 남기지만, 이 클래스 자신의
  로거(`DigestScheduler.log`)에는 아무 기록도 남지 않고, `AdminNotifier`로도 연결되지 않는다.
- **실패 시나리오**: 다이제스트 조립 중 DB 일시 장애가 나면 그 주 다이제스트가 조용히 안 나가고, 다음 주까지
  아무도 모른다(1인용이라 "안 왔다"는 걸 알아채는 게 유일한 신호).
- **이미 막는 장치 확인**: `DigestSchedulerWiringTest`는 `@EnableScheduling` 등록만 확인하고 예외 격리는 다루지
  않는다. `DigestFormatterTest`·`AlertMessageFormatterTest`도 이 스케줄러 계층은 다루지 않는다.
- **권고**: 주간 1회뿐이라 급하지 않지만, `try/catch`로 감싸 `AdminNotifier.notify(...)`를 호출하면 `PipelineScheduler`와
  같은 관측 수준을 맞출 수 있다.

### B2-04 — `SchedulingConfig` 주석이 폐기된 모듈 소유권 구분을 인용한다 · Info
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/scheduler/SchedulingConfig.java:9-10`
- **근거**: `"core는 상대 개발자 영역이라 기존 파일 수정 없이 additive로만 들어간다"` — `CLAUDE.md` 모듈 소유권
  절은 2026-07-23부로 이 구분을 폐기했다고 명시한다("그 구분은 폐기한다"). 기능에는 영향 없는 주석 드리프트.
- **실패 시나리오**: 해당 없음(문서 드리프트, 동작에 영향 없음).
- **이미 막는 장치 확인**: 해당 없음.
- **권고**: 주석을 현재 모듈 소유권 절에 맞게 갱신하거나 삭제.

## 시간·범위 한계로 못 본 것
- `HttpTelegramApi`의 실 텔레그램 API 응답 형태(특히 `getUpdates`의 실제 4xx 본문 스키마)는 토큰 미발급 상태라
  수동 스파이크로 검증하지 못했다 — B2-01의 실패 시나리오는 코드 정적 분석과 텔레그램 공개 문서(HTTP 상태 코드
  규약)에 근거한 것이며 실 네트워크로 재현하지 않았다.
- `ReviewCallbackRouter`(application 계층)·`SendDigestUseCase`(application 계층)는 리뷰 범위 밖이라 참고용으로만
  열람했고, 해당 파일에 대한 전수 리뷰는 하지 않았다.
- `UsedAlertMessageFormatter`(테스트 전무)는 코드를 정독했으나 논리적 결함을 찾지 못했다 — null/blank 방어,
  가격 포맷팅, PRICE_DROP 이전가 부재 시 폴백 모두 정상으로 보였다. 다만 테스트가 전혀 없어 향후 회귀에는
  취약하다(별도 결함은 아니므로 findings에 올리지 않음).
- Spring Boot 기본 `TaskScheduler` 풀 크기(기본값 1)를 override하는 설정이 없음을 `application.yml` 열람으로
  확인했으나, 실제 런타임에서 `PipelineScheduler`(60초 주기)와 `TelegramInboundPoller`(3초 주기)가 같은 스레드를
  공유해 파이프라인 틱이 길어질 때 버튼 응답이 지연되는 정도는 실측하지 못했다(설계 의도상 알려진 트레이드오프로
  보여 findings에는 올리지 않았다).
