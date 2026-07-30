-- V21: deal_alert.kind CHECK 제약에 PINNED_PRICE_INCREASED 추가.
-- Q-83 ④(2026-07-30 확정)가 FollowUpKind.PINNED_PRICE_INCREASED를 도입했다 — V20과 같은 결함을
-- 반복하지 않도록 이번엔 enum 도입과 같은 커밋에서 CHECK 제약을 함께 갱신한다(docs/99-lessons 교훈).
alter table deal_alert drop constraint deal_alert_kind_check;
alter table deal_alert add constraint deal_alert_kind_check
    check (kind in ('FIRST', 'VERIFIED', 'PRICE_CHANGED', 'ENDED', 'REOPENED', 'PINNED_PRICE_INCREASED'));
