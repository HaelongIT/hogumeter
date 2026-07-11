# used · 04. 인수조건 (TDD 기준)

> **이 문서가 M2 TDD의 기준이다.** 각 인수조건(AC)을 테스트로 1:1 변환한 뒤 구현한다(Red → Green → Refactor).
> 정책 정본: `docs/90-planning-final.md` §5. 순수 도메인(3계층·목록 diff·위험 신호)은 **IO 없는 순수 함수**로
> 작성해 단위 테스트만으로 검증(`docs/21`). 파서는 golden fixture(`docs/14` 테스트 포인트).

---

## USED-01. 3계층 필터 (순수 — core `domain/used`)

### AC-1. required는 AND — 하나라도 빠지면 후보 아님
- **Given** `required = [아이폰17, 256]`
- **When** 매물 title `아이폰17 128 S급`을 판정한다
- **Then** `256`이 없으므로 **후보 아님**(REJECTED). title `아이폰17 256 블랙`은 후보다.

### AC-2. bonusGroups SORT — 없어도 알림, 있으면 배지·상단
- **Given** `bonusGroups = [{keywords:[S급,에스급,민트], mode:SORT}]`, required 통과 매물 2건(하나는 `S급` 포함)
- **When** 판정한다
- **Then** 둘 다 알림 후보이나, `S급` 매물에 **배지 부여 + 정렬 상단**. SORT는 알림 발송 조건에 **기여하지 않는다**.

### AC-3. bonusGroups TRIGGER — 그룹 중 1개 이상 있어야 알림
- **Given** `bonusGroups = [{keywords:[미개봉,새제품], mode:TRIGGER}]`, required 통과 매물 2건
- **When** 판정한다
- **Then** `미개봉`·`새제품` 중 하나라도 있는 매물만 **알림 조건 충족**. 없는 매물은 required를 통과해도 알림 안 함.

### AC-4. exclude는 NOT — 히트하면 즉시 탈락
- **Given** `exclude = [부품용, 액정파손, 침수, 삽니다, 매입]`(전역) + 검색별
- **When** title `아이폰17 256 액정파손 부품용`을 판정한다
- **Then** required를 통과해도 **탈락**. exclude가 required·bonus보다 우선.

### AC-5. 동의어는 그룹 내 나열 — 내장 사전 없음
- **Given** `bonusGroups = [{keywords:[S급,에스급,민트], mode:SORT}]`
- **When** `민트` 매물을 판정한다
- **Then** 같은 그룹의 `S급`과 동일하게 취급(그룹 OR). **시스템이 `S급≈민트`를 아는 사전은 없다** — 사용자가 나열했기 때문에만 같다.

### AC-6. 정규화 — 대소문자·띄어쓰기 무관
- **Given** `required = [iPhone17]`
- **When** title `iphone 17`·`IPHONE17`을 판정한다
- **Then** 둘 다 매칭(정규화: 소문자·공백 제거). 정규화는 required·bonus·exclude에 일관 적용.

---

## USED-02. 목록 diff 생애주기 (순수 — core `domain/used`)

### AC-7. 신규 매물 감지
- **Given** 이전 스냅샷에 없던 `listingId=A`가 이번 스냅샷에 있다
- **When** 두 스냅샷을 diff한다
- **Then** `A`는 **신규**. required 통과 + (TRIGGER 충족) + targetPrice 이하면 알림.

### AC-8. 가격변동 감지 — 승격 매물 한정 후속
- **Given** `listingId=A`가 이전 900,000 → 이번 850,000
- **When** diff한다
- **Then** **가격변동**. `A.promoted=true`(알림이 나갔던 매물)면 후속 알림, 아니면 배지·정렬만(스팸 방지).

### AC-9. 판매완료(소실) 감지 — 목록에서 빠지면 SOLD 추정
- **Given** 이전 스냅샷의 `listingId=A`가 이번 스냅샷에 없다
- **When** diff한다
- **Then** `A.status = SOLD`(추정). **`A.promoted=true`인 매물만** "판매완료" 알림 — 스냅샷 전체엔 미적용.
  ⚠️ 번개 status 코드표 미실측(Q-44): `예약중`을 판매완료로 오독하면 조기 발화 — 잠정 처리임을 표시.

### AC-10. 끌올 dedupe — 같은 listingId 재등장은 신규 아님
- **Given** `listingId=A`가 이전에도 이번에도 있다(끌올로 목록 상단 재노출)
- **When** diff한다
- **Then** **신규 아님**. listingId 자연키로 dedupe해 **중복 알림을 막는다**.

### AC-11. 상세 fetch는 승격 시 1회 — 목록 폴링은 안 함
- **Given** 목록 폴링 사이클
- **When** 매물을 관측한다
- **Then** 목록에서만 판정하고 상세 페이지를 fetch하지 않는다. **알림 승격 시** `detail_fetched=false`인 매물만 1회 fetch.

---

## USED-04. URL 평가기 (core 추출 인터페이스 + web 입력)

### AC-12. 입력 3단 폴백 — URL → TEXT → MANUAL
- **Given** 평가 요청
- **When** ① URL fetch를 1회 시도 → 실패하면 ② "텍스트 붙여넣기 요청" → 그것도 없으면 ③ 수동 필드 입력
- **Then** 입력 계층은 `URL | TEXT | MANUAL` 추상화로 처리(처음부터). 어느 경로든 같은 구조화 필드로 수렴.

### AC-13. 출력 3종 — 가격맥락·위험신호·원문
- **Given** 구조화된 매물(가격·지역·등록일)
- **When** 평가기가 출력한다
- **Then** ① **가격 맥락**: 신품 기준가 대비 % + 활성 매물 스냅샷(통계 가공 없이 나열, "비교 대상: 번개장터
  활성 매물" 출처 표기) ② **위험 신호**: exclude/업자 레퍼토리 키워드 히트 + "스냅샷 최저 대비 X% 이상 저렴"
  플래그 — **나열만, 판정 문구 금지** ③ **원문 링크 + 구조화 필드**.

### AC-14. 위험 신호는 판정하지 않는다
- **Given** `이민 급처 선입금` 매물
- **When** 위험 신호를 낸다
- **Then** `업자 레퍼토리 키워드: 이민 급처`처럼 **신호를 나열**한다. "사기다"·"위험하다" 같은 **판정 문구를 쓰지
  않는다**(절대 원칙 2). 결론은 사람이 낸다.

### AC-15. 추출은 인터페이스로 분리 — v1 규칙, v2 LLM 교체 지점
- **Given** 매물 텍스트
- **When** 구조화 필드를 추출한다
- **Then** 추출기는 인터페이스 뒤에 있다. v1은 규칙 기반, v2에서 LLM으로 **교체 가능**(이 경계를 처음부터 둔다).

---

## USED-05. 메모·병렬 비교 (core EAV + web 표)

### AC-16. 자유 메모 — 글에 없는 관찰 포함
- **Given** 매물
- **When** `listing_note`에 자유 메모를 남긴다
- **Then** 저장·조회된다. 마찰 최소가 기본(구조 강제 없음).

### AC-17. 비교축은 제품 단위 정의 + 값 승격
- **Given** 제품에 `comparison_axis = [배터리%, 구성]`
- **When** 메모 값을 축으로 승격한다(`listing_axis_value`)
- **Then** 그 매물의 그 축에 값이 붙는다. 승격은 **명시적 사용자 행위**(자동 추출은 Phase 2 = LLM 자리).

### AC-18. 병렬 비교표 — 빈칸은 체크리스트
- **Given** 축 3개 + 매물 3건, 일부 축값이 비어 있다
- **When** 비교표를 그린다
- **Then** 상단 축 정렬(빈칸 노출 = "확인 안 한 항목" 체크리스트 겸용) + 하단 매물별 자유 메모 전문.

---

## 파서 (USED-02 collector — golden fixture)
- `parse_bunjang`은 `find_v2.json` golden(`tests/fixtures/bunjang/`)으로만 테스트. 실 네트워크 금지(`docs/21`).
- status 매핑은 코드표 미실측이라 잠정(비-`"0"`=SOLD_OUT). **golden 20건 중 12건이 `free_shipping:false`**라
  `배송비미상` 태그가 붙는다(이미 구현). M2 배선 시 `used_listing_observation` 적재까지 종단 검증(스모크).
