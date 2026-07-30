# 17. 기능(2차)4 — 딜 보관함 (WATCH)

> ✅ **유보 해제 — M6 착수(2026-07-25, decision-log 참조).** DN-W는 착수 자체가 해제 트리거였다(로드맵
> 문구대로). 선행 의존이던 **DealEvent 재개 전이(DN-C1)는 배선 완료**(`DealStatus` ENDED→ACTIVE,
> `DealMergePolicy.merge()` 재개 로직, `FollowUpKind.REOPENED`) — 아래 골자를 이제 구현 대상으로 본다.
> 출처: intake B-10. 성격: 알림과 구매 사이 "고민 구간"(루프의 마지막 빈칸). 대상 = **DealEvent만**(중고 매물은 기능5 관할).
> 마일스톤: M6(`docs/30`).

## 골자
- ✅ **WatchItem** = `dealEventId` + `anchorPostId`(핀 시점 소스 원문 — 재구성 자동 승계는 Q-83 ①) + 메모.
  유일성 = 딜당 활성 핀 1개(부분 유니크 인덱스). 결말 후 재핀 = 같은 dealEventId로 새 행(이력은
  자연히 이어짐). **2026-07-25 배선**(`WatchItemEntity`·`PinDealUseCase`).
- ✅ **핀 자격 닫힌 목록** = [ENDED 아님 ∧ 자격 상실(outlierFlag≠NONE) 아님 ∧ 기각 아님] — "표시된 딜"의
  정본 조건을 그대로 재사용(`PinEligibility`, 사본 아님).
- **핀 = 매칭 확정 겸함**(매칭 축만) — 미착수. 핀 이력 딜은 키워드 사후학습 근거 제외 — 미착수(Q-83 ③).
- ✅ **결말 전이**: [샀어요]→BOUGHT / 기각·해제→DROPPED(결과가 같아 상태 하나로 합침, `ResolvePinUseCase`)
  / ENDED 감지→MISSED 자동(`MarkMissedPinsUseCase`, `PipelineScheduler` 배선, "사실=자동").
- ✅ **PUR 프리필 해소(2026-07-30)**: [샀어요] → 그 딜의 variant 판단 화면으로 이동 + `PurchasePanel`
  폼에 딜 가격·오늘·연결 딜 프리필, 사람이 실지불가를 확인·수정해 [기록]. 수요축 값은 프리필하지
  않는다(판단 화면에서 고르는 기존 흐름 유지). 자격 상실 확인 알림·부활 미응답 플래그는 여전히
  미착수(Q-83 ⑤).
- **핀 후속**(딜 층, 인하·품절·종료·검증 무조건 + 인상 1회 특례) — 미착수(Q-83 ④). DealEvent 층의
  REOPENED(부활) 후속은 이미 있다(DN-C1, 핀 여부 무관하게 전 딜에 적용).
- ✅ **회고**(활성 뷰) — 2026-07-27 `WatchPage` 회고 탭으로 배선.
- ✅ **보관함 = 5번째 표면**(활성 탭/회고 탭) — 2026-07-27 배선(사용자 지시로 착수, Q-83 ⑥).

## 종속 조항 (다른 문서에서 조건부 표기 중)
- `docs/18` DIGEST ④(핀 결말 전이 + 부활) — WatchItem은 이제 있으나 DIGEST④ 자체는 여전히 WATCH 표면
  (회고·미확인 섹션) 완성 후로 대기.
- `docs/12` 후속 계열의 핀 딜 인상 특례(1회) — Q-83 ④.
- `docs/02` WatchItem 개체 — ✅ 2026-07-25 배선(V19). DealEvent 재개 전이(DN-C1)도 ✅ 배선 완료.

## 열린 것
- 회고 창 기본값(가안 3개월, UI 착수 시 확정).
- 남은 골자 조각은 `docs/91` Q-83에 정리.
