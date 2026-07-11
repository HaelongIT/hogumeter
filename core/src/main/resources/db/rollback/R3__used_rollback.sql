-- V3(used) 롤백 (REL-05: 마이그레이션마다 롤백 스크립트 동반).
-- Flyway가 자동 실행하지 않는다 — 운영 롤백 시 수동 적용 후 flyway_schema_history 정리.
--
-- **역순으로만 성립한다.** FK 의존 역순으로 지운다: axis_value·note가 listing을,
-- listing_axis_value가 comparison_axis를, listing·observation·bonus_group이 used_search를 참조한다.
-- 순서를 어기면 "other objects depend on it"으로 멈춘다. scripts/rollback-drill.sh가 매번 재현한다.

-- 인덱스는 테이블과 함께 사라지지만, 되돌리는 것을 빠짐없이 적어 둔다.
drop table if exists listing_axis_value;
drop table if exists comparison_axis;
drop table if exists listing_note;
drop table if exists listing;
drop index if exists idx_used_obs_search_observed;
drop table if exists used_listing_observation;
drop index if exists idx_used_bonus_group_search;
drop table if exists used_search_bonus_group;
drop table if exists used_search;
