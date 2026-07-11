# used · 06. TDD 가이드 · 테스트 데이터

> 전역 정본은 `docs/21-tdd-guidelines.md`. 여기는 이 모듈의 전략·픽스처만. `04-acceptance.md`의
> 각 AC를 테스트로 1:1 변환한다(Red → Green → Refactor).

## 테스트 전략 (04의 AC와 매핑)

- **단위(대다수)**: `domain/used` — 3계층 필터(AC-1~6)·목록 diff 생애주기(AC-7~11)·위험 신호 룰(AC-14)은
  **IO 0 순수** 테스트. 파라미터라이즈드 적극:
  - 3계층: required 매칭 0/1/전부 · bonusGroups SORT vs TRIGGER · exclude 히트(우선순위) · 동의어 그룹 내 ·
    정규화(대소문자·공백). exclude가 required·bonus보다 우선함을 경계로 잠근다(AC-4).
  - 목록 diff: 신규 · 가격변동 · **promoted=true 한정** 후속 · SOLD(소실) · 끌올 dedupe · 최초부터 SOLD ·
    상세 fetch 승격 시 1회. 스냅샷 2개(이전·이번)를 입력으로.
- **파서 golden**: `parse_bunjang`은 `find_v2.json` golden(`tests/fixtures/bunjang/`)으로만(AC 파서절).
  **실 네트워크 금지**(`docs/21`). status 매핑은 코드표 미실측이라 잠정(비-`"0"`=SOLD_OUT)임을 테스트에 명시.
- **평가기**: 추출을 인터페이스 뒤에 두고 **페이크로 검증**(v1 규칙, v2 LLM 교체 지점 — 이 경계를 처음부터,
  AC-15). 입력 3단(URL|TEXT|MANUAL) 각 폴백 경로. 출력에서 **위험 신호는 나열만**이고 판정 문구가 없음을
  단언한다(AC-14 — 부재의 단언).
- **적재 계약 슬라이스**: `used_listing_observation` insert-only는 @DataJpaTest+Testcontainers 얇게.
  collector↔core 계약 드리프트는 스모크 종단으로(신품 4뷰 필드 검증과 동형).
- **통합(소수)**: "번개 목록 fixture 주입 → `used_listing_observation` 적재 → 목록 diff → 3계층 필터 →
  알림 판정" 종단 1~2본(compose 스모크). EAV 비교표(AC-16~18)는 저장·조회·빈칸 렌더까지 1본.
- **금지**: 실 사이트 호출, sleep 기반, 순서 의존.

## 임계·죽은 배선 주의 (CLAUDE.md 축적 규칙 — used에 선적용)

- **임계를 넘겨야 한 번이라도 돈다**: `promoted=true 한정 후속 알림`·`targetPrice 이하`·`TRIGGER 그룹 충족`은
  최소 시나리오("매물 1건")에서 **영원히 거짓**일 수 있다. 각 임계를 넘기는 시나리오를 하나씩 만든다(딜 1건짜리
  종단은 promoted 후속·SOLD 소실을 못 탄다 — 신품 🔥 이상치 경로가 그랬다).
- **생산자를 이름으로 대라**: `used_listing_observation`을 읽는 테스트를 짜기 전에 **그 테이블에 쓰는
  프로덕션 코드**(collector 폴링)를 이름으로 댈 수 있어야 한다. 못 대면 그 기능은 죽어 있다 —
  테스트의 `save(...)`나 fake 반환값은 생산자가 아니다(`alert_policy`·`raw_deal_post`가 그랬다).
  `check-table-wiring.sh` 계열 감사를 used 테이블에도 돌린다.
- **소비처 0 감사**: USED 응답 타입의 필드를 하나씩 grep해 "소비처 0"을 찾는다(신품 `periodLowest`가
  그래서 죽어 있었다). 화면의 각 값 앞에서 "이걸로 무엇을 결정하나"를 묻는다 — 개수·id만 그리면 사람은
  원문을 열어 처음부터 다시 판단한다.
- **셀렉터 진위는 실 사이트로만**: 번개 status 코드표·품절 표식은 실 네트워크(정지조건)로만 확인된다.
  합성/golden으로 분기를 잠그되 "미검증"을 표시하고, 표식이 **파서가 읽지 않는 자리**에 있는지 먼저
  의심한다(루리웹 `[종료]`가 제목 앵커 밖에 있어 영원히 ACTIVE였다 — Q-19 교훈).

## 테스트 데이터 / 픽스처
- 빌더 패턴: `aUsedSearch().required("아이폰17","256").bonus(SORT,"S급","민트").exclude("부품용")` —
  가독성이 곧 요구 추적성.
- 경계값은 04에서 직접 도출: exclude 우선, promoted 후속 한정, targetPrice 경계, 정규화(대소문자·공백),
  끌올 dedupe(같은 listingId), 최초부터 SOLD.
- 3계층 개선/휴리스틱은 **이미 가진 golden으로 전수 측정**(fixture는 파서 검증만이 아니라 하류 규칙의
  시험대). before/after 전수 대조로 "의도한 N건만 바뀌었음"을 증명(CLAUDE.md 규칙).

## 부수효과 주입
- `Clock` — 끌올·생애주기·폴링 시각 전부 주입. `Instant.now()`/`Date.now()` 직접 호출 금지.
- persistence·평가기 추출·URL fetch — port 인터페이스로 주입, 단위 테스트에서 목/페이크.
- **포트는 계산이 아니라 주입을 테스트한다**: 배선을 지웠을 때 RED가 되는 테스트가 하나는 있어야 하고,
  그 사실을 뮤테이션으로 증명한다(“GREEN인데 죽어 있다”를 한 층 위에 다시 만들지 않게).
- 랜덤 없음 — 모든 테스트 결정적.
