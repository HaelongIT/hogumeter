-- V12 역전 — site_poll_state 제거(rollback-drill이 V 전진 → R 역순 후진을 검증한다, REL-05).
drop table if exists site_poll_state;
