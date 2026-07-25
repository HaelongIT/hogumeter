# 17. 기능(2차)4 — 딜 보관함 (WATCH)

> ✅ **유보 해제 — M6 착수(2026-07-25, decision-log 참조).** DN-W는 착수 자체가 해제 트리거였다(로드맵
> 문구대로). 선행 의존이던 **DealEvent 재개 전이(DN-C1)는 배선 완료**(`DealStatus` ENDED→ACTIVE,
> `DealMergePolicy.merge()` 재개 로직, `FollowUpKind.REOPENED`) — 아래 골자를 이제 구현 대상으로 본다.
> 출처: intake B-10. 성격: 알림과 구매 사이 "고민 구간"(루프의 마지막 빈칸). 대상 = **DealEvent만**(중고 매물은 기능5 관할).
> 마일스톤: M6(`docs/30`).

## 골자 (구현 대상)
- **WatchItem** = `dealEventId` + `anchorPostId`(이벤트 재구성 시 자동 승계 + 이력) + 메모(note 체계 일반화). 유일성 = 딜당 활성 핀 1개(결말 후 재핀 허용 + 이력 연결).
- **핀 자격 닫힌 목록** = [표시된 딜 ∧ ENDED 아님 ∧ 자격 상실 아님 ∧ 기각 아님].
- **핀 = 매칭 확정 겸함**(매칭 축만 — 축값·이상치 판정 독립). 핀 이력 딜은 키워드 사후학습 근거 제외.
- **결말 전이**: [샀어요]→BOUGHT(PUR 프리필) / ENDED 감지→MISSED 자동("종료됨(미구매)" — 판정 금지) / 자격 상실→전이 없음 + 확인 필요 알림 / 부활→전이 없음 + 알림 / 기각→DROPPED / 해제→DROPPED. 원칙 "사실=자동, 판단=수동".
- **핀 후속**(딜 층, variant 상태 무관·ARCHIVED에도): 인하·품절·종료·검증 무조건, 인상 1회만. 발송 단위 원칙(`docs/12` B-5) 적용.
- **회고**(라이브 뷰): 갭 = firstSeen 시점 as-of(회고 규약, `docs/03` 3-3), 대표 = priceMin + 범위 병기, 판정 없는 사실만.
- **보관함 = 5번째 표면**: 활성 탭(비교 컬럼 [가격=priceLast] + 메모 + 종료 48h 취소선 + 미확인 장기 핀 섹션) / 회고 탭. 목록 📌점 = 미열람 3종.

## 종속 조항 (다른 문서에서 조건부 표기 중)
- `docs/18` DIGEST ④(핀 결말 전이 + 부활) — WatchItem 자체가 아직 없어 여전히 대기.
- `docs/12` 후속 계열의 핀 딜 인상 특례(1회) — WatchItem 배선 시 함께.
- `docs/02` WatchItem 개체 — 아직 없음(다음 증분). ✅ DealEvent 재개 전이(DN-C1)는 2026-07-25 배선 완료
  (`DealStatus`·`DealMergePolicy`·`FollowUpKind.REOPENED`, `working-area/progress-log.md` 참조).

## 열린 것
- 회고 창 기본값(가안 3개월, UI 착수 시 확정).
