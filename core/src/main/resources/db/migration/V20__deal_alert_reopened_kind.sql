-- V20: deal_alert.kind CHECK 제약에 REOPENED 추가.
-- DN-C1(2026-07-25)이 FollowUpKind.REOPENED를 도입했지만 이 CHECK 제약은 그때 갱신되지 않았다 —
-- FollowUpAlertUseCase.sendFollowUps(ids, REOPENED)가 부활 이벤트마다 매번 DataIntegrityViolation로
-- 실패해 왔다(PipelineScheduler.runStep이 삼켜 조용히 stepFailures만 늘었다, Q-56). 정본: docs/91 Q-81.
alter table deal_alert drop constraint deal_alert_kind_check;
alter table deal_alert add constraint deal_alert_kind_check
    check (kind in ('FIRST', 'VERIFIED', 'PRICE_CHANGED', 'ENDED', 'REOPENED'));
