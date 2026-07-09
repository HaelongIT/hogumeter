-- V2 롤백 (REL-05: 마이그레이션마다 롤백 스크립트 동반).
-- Flyway가 자동 실행하지 않는다 — 운영 롤백 시 수동 적용 후 flyway_schema_history 정리.
--
-- **역순으로만 성립한다.** purchase는 variant·deal_event를 참조하므로 R1(변형·딜 삭제)보다
-- 먼저 실행해야 한다. 순서를 어기면 R1이 "other objects depend on it"으로 멈춘다.
-- 그 사실을 `scripts/rollback-drill.sh`가 매번 재현해 확인한다.

-- 인덱스는 테이블과 함께 사라지지만, 되돌리는 것을 빠짐없이 적어 둔다.
drop index if exists idx_purchase_variant;
drop table if exists purchase;
