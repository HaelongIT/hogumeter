# 30. 로드맵 & 마일스톤

## M0 — 기반 + 상세설계 마감 (개발 첫 작업)
산출물:
1. 레포 스캐폴딩: core(Gradle, 01 문서 패키지 구조) + collector(Python) + compose + Flyway V1 + CI(테스트 실행)
2. **수치 초안 확정 제안**: ±α(병합 가격 허용폭), 병합 윈도우(24 vs 48h), 이상치 컷(IQR 배수), 콜드스타트 대박딜 폭(30% 가안 검증), reactionScore 정규화 방식 → `docs/31-detailed-params.md`로 문서화(근거 포함). 운영자 승인 후 상수화.
3. 스키마 DDL(02 문서 기반) + ERD
4. **스파이크(spikes/, 테스트 아님)**: 뽐뿌·루리웹·펨코 목록 실측(구조, 백필 가능 깊이), 번개 목록 실측, 네이버 API "아이폰 17 256" 응답 품질, 당근 공유 URL의 IDC fetch 가능 여부 1회 확인 → 결과 전부 98-field-notes 기록 + golden fixture 채취
- 스파이크에서 기획 전제가 깨지는 발견이 나오면 91-open-questions에 기록하고 운영자 결정 대기.

## M1 — 신품 코어 루프 (최우선 가치)
범위: BM-01~07 + AL 전체 + REG(웹 최소 슬라이스: 등록·후보선택·축·정책 설정) + 텔레그램 봇(알림+인라인 버튼)
완료 기준(= 1차 검증): **아이폰 17 일반 256GB 등록 → 백필 → 실 핫딜 유입 → 기준가가 운영자 체감과 부합 → 텔레그램 알림 수신 → 버튼으로 미상 분류 동작.** 오알림이 키워드 사후학습으로 수렴하는지 1~2주 관찰.

### 진행 상태 (2026-07-10)

| 조각 | 상태 |
|---|---|
| BM-01~07 순수 도메인 + REST | ✅ GREEN |
| collector 수집→적재 전 구간 | ✅ GREEN (`docker compose up` + `scripts/smoke.sh`). **단, 파서 정확도는 별개다** — 2026-07-10에 골든 전수 감사로 다섯 결함을 찾아 고쳤다: 루리웹 **품절 감지가 통째로 죽어 있었고**(마커가 제목 앵커 밖, 28딜 중 3건), `그래픽카드`가 카드할인으로, `5000만화소`가 5천만원으로, `RTX 5070`이 5,070원으로 읽혔고, 펨코·뽐뿌의 **숫자 배송비가 조용히 버려졌다**. 전부 "모든 테스트 GREEN"인 채였다. 남은 것: 뽐뿌 `.end2` golden 커버리지 0(Q-19), 배송 무표기 딜(Q-64), 가격 서열(Q-63)·`DDR5 5600`(Q-65) |
| **파이프라인 트리거**(`raw_deal_post` → `deal_event`) | ✅ GREEN — `PipelineScheduler`(ingest → 가격 재처리 → 종료 판정, 60초). **2026-07-10까지 없었다.** 그동안 수집된 원문을 아무도 소비하지 않아 기준가 표본이 영원히 0이었다(Q-27 ⑤) |
| AL 판정(트리거·게이트) | ✅ GREEN (발송은 스텁) |
| AL-03 **후속 알림**(VERIFIED·PRICE_CHANGED·ENDED) | ✅ **배선·이력·발송 완성**(2026-07-11~21). 2026-07-11 "🔴 없다"는 실측이었으나 이후 전부 이어졌다 — `FollowUpAlertUseCase`(멱등 `(deal_event_id, kind)` 이력)를 `PipelineScheduler`가 매 틱 태우고(Q-57에서 발송 수 카운터까지), `deal_alert` 이력이 `alreadyAlerted`를 채우며(그 "저장소 부재"는 거짓 봉인이었다 — 이미 있었다), 2026-07-21 `TelegramAlertSender`로 실 발송까지 붙었다. `ENDED`는 "지금 사라"를 받고 달려간 사람에게 **"끝났다"를 말할 유일한 경로** — 이제 산다. 남은 건 봇 토큰(외부)뿐. `docs/91` Q-67·Q-20 |
| **알림 정책 저장**(REG-03, `alert_policy` writer) | ✅ GREEN — `AlertPolicyController` + web `AlertPolicyPanel`. **2026-07-10까지 writer가 없었다.** `EvaluateAlertOnDealUseCase`가 읽기만 해서 확정본 §107의 "목표가 이하" 트리거와 방해금지(AL-04)가 발화할 수 없었다(Q-48). 저장되는 건 넷(목표가·판정 기간·방해금지 2개) — K_display·제외 키워드·⚠️라벨 토글은 엔티티 미매핑 |
| REG 웹 최소 슬라이스(등록+목록+알림 정책) | ✅ GREEN |
| web 판단 화면(신호등·기준가·갭·주기) + 구매 기록(PUR) | ✅ GREEN (M4·M5에서 앞당김, `decision-log` 2026-07-09) |
| **미상 큐 조회**(`GET /api/v1/review-queue` + web 탭) | ✅ GREEN(읽기만) — 그전까지 `review_queue_item`은 **쓰이기만 하고 아무도 읽지 않았다.** 매칭이 무엇을 놓치는지 볼 방법이 없었다. 2026-07-10: 이상치가 **왜 싸 보이는지**(조건 태그)까지 낸다 — `배송비미상`이면 "실제 결제가는 더 높습니다"를 덧붙인다. **승격·기각(쓰기)은 없다**(Q-15) — M1 완료 기준의 "버튼으로 미상 분류"는 여전히 텔레그램(Q-20) 대기 |
| OBS-04 헬스체크(컴포넌트별) | ✅ GREEN — `GET /api/v1/health`, DB가 죽으면 503 + 어느 컴포넌트인지 지목. `scripts/smoke.sh` 0-1이 postgres만 죽여 확인 |
| SEC-08 차단 신호 감지 | ✅ GREEN — **2026-07-10까지 죽어 있었다.** `urllib_opener`가 403/429를 예외로 던져 `classify_status`가 차단을 볼 수 없었다. 리허설: `scripts/check-robots-drill.sh` |
| SEC-08 **`Crawl-delay` 준수** | ✅ GREEN — **2026-07-10까지 한 번도 지킨 적이 없었다.** `effective_interval_with_robots`는 존재했고 단위 테스트도 GREEN이었지만 **프로덕션 호출자가 0**이었다(`run_cycle`은 우리 하한만 봤다). 뽐뿌가 `Crawl-delay: 120`을 선언해도 60초마다 두드렸을 것이다. `__main__._interval_port`가 `run_cycle(interval_for=…)`로 주입한다 |
| **파서 정확도**(BM-01·BM-02) | ⚠️ **2026-07-10 골든 전수 감사로 다섯 결함 수정** — 루리웹 품절 감지 사망 · `그래픽카드`=카드할인 · `5000만화소`=5천만원 · `RTX 5070`=5,070원 · 배송비 조용한 0(4곳). **전부 모든 테스트가 GREEN인 채였다.** 남은 것: 뽐뿌 `.end2` 커버리지 0(Q-19) · 실물 무표기 배송(Q-64) · 가격 서열(Q-63) · `DDR5 5600`(Q-65) |

**완료 기준을 막고 있는 것:**

0-2. **✅ 대부분 해소(2026-07-10~21) — `docs/91` Q-46**: 조건 태그(`카할`·`배송비미상` 등)가 `raw` jsonb에 갇혀 `deal_event`에 도달하지 않던 것을 고쳤다. "core 기존 파일이라 조율"이라는 봉인은 **거짓이었다**(Q-50·Q-48에 이어 세 번째) — 신규 `PreserveAppliedConditionsUseCase`(네이티브 SQL, 멱등)를 `PipelineScheduler`가 ingest 바로 뒤에 부른다. 소비자도 함께: `PipelineTickReport.conditionalTotal`. **① 표시**: `DealEvent.appliedConditions` → `BenchmarkView.DealRef.conditions` → web `conditionsSuffix`("조건부: 카할")·미상 큐 "실제 결제가는 더 높습니다"까지 종단으로 말한다. **② 하향 편향(실 폴링 전 필수)은 닫혔다**: `배송비미상`은 저장가가 하한이라 `DealSets.pricingSet`이 값 통계에서 뺀다(발생·신호엔 남긴다) — 컬럼→매퍼→계산기 종단을 `GetBenchmarkUseCaseTest.shippingUnknownDealIsExcludedFromBenchmarkThroughTheColumn`이 잠갔다. **남은 것**: 알림 본문의 조건 표시(텔레그램 어댑터 Q-20과 함께 — 발송이 스텁이라 지금 지어 넣으면 검증 못 하는 죽은 문구다).

0-1. **⚠️ 코드 안의 블로커 — `docs/91` Q-27 ④(실측 2026-07-10)**: 매칭 실패 원문은 `deal_event_source` 링크를 만들지 않아 `findUnprocessed()`가 계속 미처리로 본다 → **매 틱 다시 리뷰 큐에 쌓인다**(운영 60초 주기면 원문 하나당 하루 1,440행). 조회는 접어서 `occurrences`로 세어 보여주지만(숨기지 않는다), **승격·기각은 이게 고쳐져야 가능하다** — 하나를 처리해도 나머지가 남는다. core 기존 파일 수정이라 상대와 조율.

0. **✅ 해소(2026-07-11) — `docs/91` Q-27 ③**: 최초 수집 시 이미 품절인 원문에 "지금 사라" 알림이 나가던 결함. `candidateFrom`이 `DealStatus.fromRawPostStatus(post.getStatus())`로 초기 상태를 정하고(SOLD_OUT/DELETED→ENDED), `AlertEvaluator`가 ENDED 딜을 억제한다. 관통 테스트 `initiallySoldOutPostIsBornEndedAndNotAlerted`(스파이 sender로 send 0회). **core 소유권 조율로 우리가 수정**(이전엔 "상대와 조율"로 봉인).

**나머지는 코드 밖:**

1. **텔레그램 봇 토큰 미발급**(Q-20) → **아웃바운드 발송 어댑터는 완성**(2026-07-21, `TelegramAlertSender` + 본문 포맷터 + SEC-08). 사용자가 `.env`에 `TELEGRAM_ENABLED=true`+토큰+chat_id를 채우면 알림이 실제로 나간다 — "텔레그램 알림 수신"은 토큰만 있으면 검증된다. 아직 남은 건 **인바운드**(인라인 버튼 승격 → 미상 분류)로, 그 커밋에 SEC-03 인바운드 화이트리스트가 함께 든다(Q-61). 아웃바운드는 설정된 chat 하나로만 나가 이미 안전.
2. **네이버 API 키 미발급**(Q-3) → 현재가(BM-06 `currentPrice`)·갭 계산·REG-01 후보 검색 불가.
3. **실 폴링 미가동** → `COLLECTOR_ALLOW_NETWORK=1`은 운영자 승인 사항. 실 데이터가 없으면 "기준가가 운영자 체감과 부합"을 대조할 수 없다. 켜기 전에 **사람이** `ALLOW_REAL_ROBOTS=1 bash scripts/check-robots.sh`를 한 번 돌린다(`pre-deploy §F`).
4. **백필(REG-04) 미구현** → `raw_deal_post`에 `origin` 컬럼이 없다(있는 건 `deal_event.origin`). 계약 변경이 선행.
5. **`decisions-needed` D-3 미결** → 차단당한 사이트 재개 경로가 없어 폴링 커서를 영속화할 수 없다(REL-03, Q-59).

> 1·2·3은 사람이 발급·승인해야 열린다. 4·5는 core 스키마·정책 결정이 선행한다.
> **⚠️ 보드가 "막혔다"고 적어 두면 다음 세션은 그 주장을 검증하지 않는다** — Q-50·Q-48은 둘 다 "core 기존 파일이라 조율"로 봉인돼 있었으나 실제로는 신규 파일만으로 끝났다. 재개 트리거는 "무엇이 참이 되어야 하는가"로 쓴다(CLAUDE.md 작업 방식).

## M2 — 중고 (USED 전체)
번개 폴링 + 3계층 + 생애주기 알림 + 평가기(3단 입력) + 메모·축·병렬 비교(웹).

## M3 — 구매 비교 (CMP) + 크롬 확장
온디맨드 비교 화면 + extension ingest + 확장 개발 + 반자동 폴백.

## M4 — 웹 마감
조회·비교 대시보드, reviewQueue 웹 화면, 설정 전체, 이상치 토글.

## M5 — 판단 보조 표면 (2차 기획, 신규 수집 0)
> 출처: `working-area/2nd-plan-intake.md`, 마일스톤 결정 DN-M(2026-07-08). 순서 = 의존도·비용 오름차순.
- 기반: 딜 집합 명명·시간 좌표계·가격 3분법(`docs/03`) 순수 규칙 이식.
- **SIG + CAD**(`docs/16`): 신호등·딜 주기 read-model(가장 저비용, 나머지가 소비할 표면 선확보).
- **PUR**(`docs/15`): 구매 기록·관찰 모드·성적표(구매 이후 루프).
- **DIGEST**(`docs/18`): 주간 요약(④ 핀 결말은 WATCH 배치 전까지 비활성).

## M6 — 보관함·우선순위 (2차, 조건부)
- **WATCH**(`docs/17`): 딜 보관함 — 개념 채택·M6 배치(DN-W). 배치 착수 시 유보 해제 확정 + DealEvent 재개 전이(DN-C1) 배선.
- **PRI**(`docs/19`): 우선순위 — ②축소(목록 정렬만, DN-P). 알림 병기는 실사용 필요 확인 후 재론.

## 상시
- 각 마일스톤 종료 시: lessons 정리 → CLAUDE.md 규칙 승격 검토, NFR 체크리스트 통과 확인.
- Phase 2 장부(확정본 11절)는 마일스톤 중 임의로 건드리지 않는다. 유혹이 생기면 open-questions에. **(2026-07-08 2차 기획 통합에서 일부 항목이 M5/M6로 정식 승격 — 확정본 v1.3 §11·§13 반영. 이후 추가 승격도 동일하게 기획 세션에서만.)**
