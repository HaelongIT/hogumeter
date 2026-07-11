# used · 02. 데이터 모델 · V3 스키마 방향

> 개체 정본: `docs/02-domain-model.md` §중고. **이 문서는 방향이다** — 실제 마이그레이션(V3+)은
> core가 TDD로 확정한다(Flyway = core 단독 소유). 여기서는 개체·자연키·모듈 계약을 못박아,
> collector(적재)와 core(소비)가 같은 계약을 보게 한다.

## 개체 관계

```
Product (기존, V1)
  └─ UsedSearch (Product 종속)         ── 3계층 키워드·목표가·폴링주기
        └─ used_listing_observation    ── collector가 적재하는 목록 스냅샷 (insert-only)
        └─ Listing                     ── 매물 1건 (목록 diff가 도출·갱신)
              ├─ listing_note           ── 자유 메모
              └─ listing_axis_value     ── 승격된 축 값
  └─ comparison_axis (Product 종속)     ── 제품별 비교축 정의
```

## 개체 정의 (`docs/02` §중고 상세화)

### UsedSearch (Product 종속)
번개 검색 하나 = 3계층 키워드 + 목표가 + 폴링 주기. 등록 흐름: **제품 먼저 → 그 아래 중고 검색 추가**.

| 필드 | 의미 |
|---|---|
| `platform` | `BUNJANG`(v1). 당근은 UsedSearch 없이 평가기로만(반자동) |
| `required_keywords[]` | AND — 전부 매칭돼야 후보 (제품 정체성) |
| `bonus_groups[]` | OR 그룹. 각 그룹 = `{keywords[], mode}`, mode ∈ `SORT`(배지·정렬) \| `TRIGGER`(알림 조건) |
| `exclude_keywords[]` | NOT — 검색별. 전역 기본값·사후학습은 별도(신품 exclude와 동일 메커니즘) |
| `target_price` | 이하면 알림(선택) |
| `poll_interval_min` | 폴링 주기, 기본 10 (하한은 `SiteKind.MARKETPLACE` 10분과 정합) |

- 동의어는 `bonus_groups`의 한 그룹 안에 사용자가 나열(`{keywords: ['S급','에스급','민트'], mode: SORT}`).
  **내장 동의어 사전을 만들지 않는다.**
- `bonus_groups`는 **정식 구조**(그룹·모드)라 JSONB로 뭉치지 않는다 — 그룹 테이블 + 키워드 테이블 또는
  배열 컬럼. core가 TDD로 정규화 형태 확정.

### Listing (중고 매물)
| 필드 | 의미 |
|---|---|
| `platform` | `BUNJANG` \| `DAANGN`(평가기 수동 생성) |
| `listing_id` | 플랫폼 매물 ID — **자연키**(끌올 dedupe·소실 판정의 기준) |
| `title` | 매물 제목 (3계층 필터 입력) |
| `price` 이력 | 가격 변동 추적 (목록 diff가 갱신) |
| `status` | `ACTIVE → SOLD / REMOVED` (목록 소실 = SOLD 추정) |
| `promoted` | 알림 승격 여부 — **후속 알림(가격변동·판매완료)은 이 값이 true인 매물 한정** |
| `detail_fetched` | 승격 시 1회 상세 fetch 완료 여부 |

### used_listing_observation (collector 적재 계약 테이블 — insert-only)
- **collector가 쓰고 core가 읽는다.** 번개 목록 폴링 산출물의 **원시 스냅샷**. 신품의 `raw_deal_post`와
  별개 — 중고는 목록 diff에 스냅샷 이력이 필요하고, condition/marketplace 맥락이 다르다.
- **insert-only**(`docs/01` line 75): 관측을 덮어쓰지 않고 쌓는다. core의 목록 diff가 연속 두 스냅샷을 읽어
  생애주기를 판정한다. Listing 상태(promoted·status)의 판정·갱신은 **core의 몫**이지 collector가 아니다.
- 자연키/멱등: 같은 폴링 사이클의 같은 `listing_id`는 한 번만(스냅샷 단위 dedupe). 세부는 core V3가 확정.
- SEC-07: 개인정보(판매자 uid·동 단위 location·광고 추적 imp_id)를 담지 않는다(`parse_bunjang`이 이미 걸러냄).

### 메모·축 (EAV 3테이블 — JSONB 금지)
`docs/90` §5·`docs/14` USED-05. **JSONB로 뭉치지 말 것**(JSONB는 크롤링 원본 보관 전용).

| 테이블 | 의미 |
|---|---|
| `listing_note` | 매물별 자유 메모(글에 없는 관찰 포함 가능). 마찰 최소가 기본 |
| `comparison_axis` | **제품 단위** 비교축 정의(`배터리%`·`구성` — 제품마다 중요한 축이 다름) |
| `listing_axis_value` | 메모 값을 축으로 **승격**한 것(선택적, 비교가 필요해진 시점에) |

## 모듈 계약 (경계를 넘는 것)

- **collector → used_listing_observation**: collector가 쓰는 컬럼 집합이 계약. core V3가 그 스키마를 확정하면
  collector가 맞춘다. **드리프트는 스모크 종단 검증으로 잡는다**(신품 4뷰 필드 검증과 동형 — `scripts/smoke.sh`).
- **UsedSearch → collector 검색어**: 검색어 소스는 `01-architecture` §열린 이음새 1번. 잠정은 collector 설정 주입.
- **Listing.status ↔ 번개 status 코드**: `parse_bunjang`의 status 매핑(현재 비-`"0"`=전부 SOLD_OUT, 잠정)이
  Listing 생애주기의 입력이다. **Q-44 코드표 미실측 → 예약중을 판매완료로 오독하면 조기 알림.** 실측은 사람 몫.

## V3 마이그레이션 방향 (core가 TDD로 확정)
- 새 테이블: `used_search`(+ bonus 그룹 정규화) · `used_listing_observation`(insert-only) · `listing` ·
  `listing_note` · `comparison_axis` · `listing_axis_value`.
- 파일 번호: **V3+** (V2는 `V2__purchase.sql`이 소진 — `docs/91` Q-4 정정).
- 롤백: 각 V에 짝 R(REL-05, 역순). `used_search`는 `product`를 참조하므로 롤백 순서에 주의(신품 `purchase`가
  `variant`를 참조한 것과 같은 계열 — `docs/99` 2026-07-08 교훈).
