# used · 03. API 명세

> **아래는 전부 제안(미적용)이다.** USED의 REST 표면은 core(상대 개발자)가 V3 도메인과 함께
> 확정한다 — 여기서 엔드포인트·필드를 못박으면 다음 세션이 그것을 요구사항의 제약으로 읽어
> core의 손을 묶는다(`docs/91` Q-50 교훈). 이 문서는 **모양의 방향**만 준다: 무엇을 노출해야
> 하는지(등록·조회·평가·비교), 어떤 정직성 계약이 응답에 살아야 하는지. 전역 API 컨벤션(응답
> 봉투·에러 형식·인가)은 M1 web 슬라이스가 확정한 것을 따른다(`docs/91` Q-2).

## 노출해야 하는 것 (기능 → 표면)

| 기능 | 표면(제안) | 소유 | 비고 |
|---|---|---|---|
| USED-01 검색 등록 | 제품 종속 UsedSearch 생성/수정/삭제 | core+web | required/bonusGroups[mode]/exclude/targetPrice/pollInterval |
| USED-02 매물·생애주기 조회 | UsedSearch별 Listing 목록(status·가격이력·promoted) | core+web | 목록 diff 산출물. 무가공 나열 |
| USED-04 평가기 | 매물 평가 요청(URL\|TEXT\|MANUAL) → 3종 출력 | core+web | 플랫폼 무관 입구 |
| USED-05 메모·축·비교 | listing_note·comparison_axis·listing_axis_value CRUD + 비교표 조회 | core+web | EAV. 값 승격은 명시적 행위 |

> 알림 발화·텔레그램 인라인 버튼(승격 액션)은 **기능3(AL) 모듈의 API** — 이 문서 범위 밖(BM과 동형).

## 엔드포인트 (제안 — core V3가 확정)

### USED-01. 검색 등록
- **`POST /api/v1/products/{productId}/used-searches`** (제안) — 제품 아래 중고 검색 추가.
  등록 흐름은 "제품 먼저 → 그 아래 검색"(`docs/used/02`).
- 본문(방향): `platform`(BUNJANG v1) · `requiredKeywords[]` · `bonusGroups[{keywords[], mode}]`
  (mode ∈ `SORT`|`TRIGGER`) · `excludeKeywords[]` · `targetPrice?` · `pollIntervalMin`(기본 10, 하한 10).
- `bonusGroups`는 **정식 구조**라 JSONB로 뭉치지 않는다(`docs/used/02`) — 요청 스키마도 그룹·모드를 드러낸다.

### USED-02. 매물·생애주기 조회
- **`GET /api/v1/used-searches/{id}/listings`** (제안) — 이 검색이 도출한 Listing.
- 응답(방향, **무가공 나열** — 절대 원칙 1): 매물별 `listingId`·`title`·`price`(이력)·`status`
  (ACTIVE|SOLD|REMOVED)·`promoted`·관측시각. **median·P25 같은 통계 필드를 만들지 않는다**(중고는
  기준가 합성 금지). 신품 기준가 대비 %는 평가기(아래)가 맥락으로만 낸다.

### USED-04. 평가기 (플랫폼 무관 입구)
- **`POST /api/v1/used/evaluate`** (제안) — 입력 3단 폴백을 한 추상으로 받는다(`docs/used/04` AC-12).
- 본문(방향): `{ kind: "URL"|"TEXT"|"MANUAL", payload }`. URL fetch 실패 → 텍스트 붙여넣기 → 수동 필드.
  어느 경로든 같은 구조화 필드로 수렴.
- 응답(방향, 3종 — `docs/used/04` AC-13): ① **가격 맥락**(신품 기준가 대비 % + 활성 매물 스냅샷 나열,
  `"source": "번개장터 활성 매물"` 출처 상시) ② **위험 신호**(exclude/업자 레퍼토리 키워드 히트 +
  "스냅샷 최저 대비 X% 저렴" — **나열만, 판정 문구 금지**, AC-14) ③ **원문 링크 + 구조화 필드**.

### USED-05. 메모·축·비교표
- **`POST /api/v1/listings/{id}/notes`** (제안) — 자유 메모(글에 없는 관찰 포함, 마찰 최소).
- **`PUT /api/v1/products/{productId}/comparison-axes`** (제안) — 제품 단위 비교축 정의.
- **`POST /api/v1/listings/{id}/axis-values`** (제안) — 메모 값을 축으로 **승격**(명시적 사용자 행위).
- **`GET /api/v1/products/{productId}/comparison`** (제안) — 병렬 비교표(상단 축 정렬, 빈칸 노출 =
  미확인 체크리스트; 하단 매물별 자유 메모 전문, `docs/used/04` AC-18).

## 응답 정직성 계약 (표시 계층 재량이 아니라 도메인 계약)

- **기준가를 합성하지 않는다**: USED-02·04 응답에 중고 매물의 median·P25·분포 통계가 **없다**.
  상태 편차(S급·부품용·침수) 때문에 분포 합성이 무의미하다(`docs/used/00`). 가격 맥락은 스냅샷 나열 +
  신품 기준가 대비 %만.
- **출처 상시 표기**: 스냅샷에 "비교 대상: 번개장터 활성 매물"을 붙인다(당근 매물 평가 시 직거래/택배
  맥락 차이 인지용). BM의 `present.ts` 정직성 강제와 동형.
- **위험 신호는 나열만**: "사기다"·"안전하다" 같은 판정 문구를 응답에 넣지 않는다(절대 원칙 2). 결론은 사람.
- **미실측을 값으로 위장하지 않는다**: 번개 status 코드표 미실측 구간(Q-44)은 `status`를 잠정 `SOLD`로
  두되 그 사실을 응답에 표시(예: `statusProvisional: true`). "값 없음"을 정상 응답 모양의 거짓말로 흘리지
  않는다(CLAUDE.md 축적 규칙, Q-53의 거울상).

## 인가 매트릭스 (제안 — BM과 동형, Q-2 확정 따름)
| 엔드포인트 | 공개 | 인증 | 비고 |
|---|---|---|---|
| 위 USED-01~05 (제안) | 사설망 한정 | 없음 | 공개망 노출 시 최소 인증 추가(`docs/90` §10 이월). nginx `location /api/`도 인증 대상(SEC 교훈) |

> **평가기 URL fetch는 실 네트워크다** — 무중단 정지조건(사용자 승인). 당근 URL 자동 fetch는 플랫폼
> 잣대 ②로 **시도 금지**(반자동, 네이티브 알림 위임 — `docs/used/00` 절대 원칙 연결). 번개 평가는 이미
> 폴링한 스냅샷을 재사용할 수 있어 추가 fetch가 불요할 수 있다(core가 확정).
