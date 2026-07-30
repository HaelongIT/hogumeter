-- V22: watch_item.revive_unacknowledged — 부활 미응답 플래그(Q-83 ⑤, 2026-07-30 확정).
-- ACTIVE 핀의 딜이 부활(ENDED→ACTIVE, DN-C1)하면 이 플래그가 서고, 사람이 [확인함]을 누르면 내려간다.
-- 핀 결말 전이는 없다("전이 없음+알림+미응답 플래그 대체" — 2nd-plan-intake B-10 결말표).
alter table watch_item add column revive_unacknowledged boolean not null default false;
