-- R20: V20 역. 기존 4종으로 복귀 — 되돌리기 전 REOPENED 행이 있으면 이 제약 재추가가 실패한다(의도적,
-- 데이터 손실 없이 실패로 알린다).
alter table deal_alert drop constraint deal_alert_kind_check;
alter table deal_alert add constraint deal_alert_kind_check
    check (kind in ('FIRST', 'VERIFIED', 'PRICE_CHANGED', 'ENDED'));
