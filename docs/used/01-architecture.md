# used · 01. 아키텍처 · 모듈 경계

> 정책 정본: `docs/90-planning-final.md` §5. 모듈 소유권: `CLAUDE.md`(core=상대 개발자, collector·web·docs=우리).
> USED는 **세 컴포넌트에 걸친다** — 경계와 계약을 여기서 못박는다. 계약이 모듈 경계를 넘으면
> 한쪽이 소유하고 다른 쪽은 원본을 참조한다(`CLAUDE.md` 축적된 규칙).

## 파이프라인 한눈에

```
[web] 3계층 검색 등록(UsedSearch)  ─┐
                                    ▼
[collector] 번개 목록 폴링 ── find_v2.json?q={검색어} ── parse_bunjang ── used_listing_observation 적재(insert-only)
                                    │
                                    ▼
[core] 목록 diff(순수) ── 신규·가격변동·판매완료·끌올 ── Listing 생애주기 ── 3계층 필터(순수) ── 알림 판정
                                    │
                                    ├── URL 평가기(추출 인터페이스) ── 가격맥락·위험신호·구조화필드
                                    └── 메모·축(EAV) ── 병렬 비교표
                                    ▼
[web] 평가기 3단 입력 · 병렬 비교표 · 스냅샷 나열
```

## 모듈별 책임

### collector (우리 소유) — 번개 목록 폴링
- **입력**: 검색어별 URL(`https://api.bunjang.co.kr/api/1/find_v2.json?q={검색어}&order=date&page={n}&n={건수}`,
  `docs/98`). 검색어의 정본은 `UsedSearch`(core). collector는 **DB 읽기 경로가 없으므로**(현재 `raw_deal_sink`는
  쓰기 전용) 검색어를 받는 방법이 선결이다 — §"열린 이음새" 참조.
- **파싱**: `parse_bunjang`(이미 존재, `docs/98`) — `find_v2` JSON → `ParsedDeal`. 프로덕션 호출자 0(M2에서 배선).
  SEC-07: `raw`에 `ad`·`bizseller`·`free_shipping`만(uid·location·imp_id 금지, `tests/test_privacy.py`가 잠금).
- **적재**: `used_listing_observation`(core 소유 계약 테이블)에 **insert-only 목록 스냅샷**. 신품의
  `raw_deal_post`와 **다른 테이블**이다(중고는 condition·marketplace 맥락이 다르고, 목록 diff는 스냅샷 이력이 필요).
- **레이트**: `SiteKind.MARKETPLACE` 하한 10분(`scheduler/policy._FLOOR`, 이미 정의). 설정으로 완화 불가(SEC-08).
- **상세 fetch**: 목록 폴링에서 **하지 않는다**. 알림 승격 시 core/알림 경로가 1회 fetch(판매자 설명 확보).

### core (상대 개발자 소유) — 판정·생애주기·평가기·메모/축
- **3계층 필터**(USED-01): `domain/used`의 **순수 함수**. `title` 문자열 + `UsedSearch`(required/bonus/exclude)
  → 매칭 결과(후보 여부 · SORT 배지 · TRIGGER 충족). IO 없음 → 단위 테스트만으로 검증(`docs/21`).
- **목록 diff 생애주기**(USED-02): **순수 함수** — 연속 두 스냅샷(이전 `used_listing_observation` vs 이번) 입력 →
  신규·가격변동·판매완료(소실)·끌올(listingId dedupe) 판정. `Listing.status` 전이(ACTIVE→SOLD/REMOVED).
- **URL 평가기**(USED-04): **추출을 인터페이스로 분리**(v1 규칙, v2 LLM 교체 지점). 출력 3종 조립.
  스냅샷(활성 매물 나열)은 번개 폴링 산출물을 **통계 가공 없이** 낸다.
- **메모·축**(USED-05): EAV 3테이블 읽기·쓰기. 값 승격(메모→축)은 명시적 사용자 행위.
- **스키마**: V3+ 마이그레이션(Flyway = core 단독 소유). `02-data-model.md`가 방향, TDD로 확정.

### web (우리 소유) — 검색 등록·평가기 입력·비교표
- **3계층 검색 등록**: 제품 아래 UsedSearch 추가(required/bonus 그룹[모드]/exclude 입력). 동의어는 그룹 안에 나열.
- **평가기 3단 입력**: `URL | TEXT | MANUAL` 추상화(처음부터). URL fetch 실패 → 텍스트 붙여넣기 → 수동 필드.
- **병렬 비교표**: 상단 축 정렬(빈칸 노출 = 미확인 체크리스트), 하단 매물별 자유 메모 전문.
- **정직성 표시**: 스냅샷에 "비교 대상: 번개장터 활성 매물" 출처 상시. 위험 신호는 나열만(판정 문구 금지).
  기준가 대비 %만 그리고 시세 합성은 하지 않는다(`decision/present.ts`가 신품에서 한 것과 같은 정직성 강제).

## TDD 이음새 (IO 없는 순수 경계)

| 순수 도메인 | 입력 | 출력 | 소유 |
|---|---|---|---|
| 3계층 필터 | `title`, UsedSearch(required/bonus/exclude) | 후보 여부·SORT 배지·TRIGGER 충족 | core |
| 목록 diff | 스냅샷 2개(이전·이번) | 신규·가격변동·판매완료·끌올 | core |
| 알림 판정 | 필터 결과 + targetPrice + 매물 가격 | 발송 여부 | core |
| 위험 신호 룰 | 매물 필드 + exclude/업자 키워드 | 신호 나열(판정 없음) | core |

파서(`parse_bunjang`)는 **golden fixture**로만 테스트(실 네트워크 금지, `docs/21`).

## 🔴 열린 이음새 (M2 착수 시 정해야 할 모듈 경계)

1. **검색어 소스**: collector가 `UsedSearch`의 검색어를 어떻게 받나. 후보 — (a) core가 검색어 목록 API/뷰를
   제공하고 collector가 신규 read 어댑터로 폴링, (b) collector 설정(환경변수)로 잠정 주입(refactor seam, 나중에 a로 교체).
   **가장 보수적**: (b)로 시작해 종단 파이프라인을 세우고, core V3가 서면 (a)로 승격. `docs/91`에 잠정값으로 기록.
2. **적재 계약**: `used_listing_observation`의 컬럼(collector가 쓰고 core가 읽는다). core V3가 확정하면 collector가
   그 계약에 맞춘다. 계약 드리프트는 스모크 종단 검증으로 잡는다(신품의 4뷰 필드 검증과 동형).
3. **번개 status 코드표**(Q-44): 예약중 vs 판매완료 실측은 **실 네트워크(정지조건)**라 사람 몫. 미실측 구간은 잠정
   SOLD_OUT, 생애주기 판정이 이 코드표에 종속됨을 명시.
