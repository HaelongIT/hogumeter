-- V4(alert_history) 롤백 (REL-05). 역순. deal_alert는 deal_event를 참조하므로 R1(딜 삭제)보다 먼저.
-- Flyway가 자동 실행하지 않는다 — 운영 롤백 시 수동 적용 후 flyway_schema_history 정리.
drop index if exists idx_deal_alert_deal;
drop table if exists deal_alert;
