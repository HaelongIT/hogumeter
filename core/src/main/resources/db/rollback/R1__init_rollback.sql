-- V1 롤백 (REL-05: 마이그레이션마다 롤백 스크립트 동반).
-- Flyway가 자동 실행하지 않는다 — 운영 롤백 시 수동 적용 후 flyway_schema_history 정리.

drop table if exists global_setting;
drop table if exists review_queue_item;
drop table if exists alert_policy;
drop table if exists price_history;
drop table if exists deal_event_source;
drop table if exists deal_event;
drop table if exists raw_deal_post;
drop table if exists alias_dictionary;
drop table if exists variant;
drop table if exists product_axis;
drop table if exists product;
