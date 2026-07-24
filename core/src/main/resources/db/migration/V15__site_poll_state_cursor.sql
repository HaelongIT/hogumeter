-- V15: 폴링 커서 영속화 (REL-03, Q-59) — D-3(차단 사이트 재개 경로) 확정 후 진행.
-- 롤백: db/rollback/R15__site_poll_state_cursor_rollback.sql
--
-- V12은 "성공만 적는다"며 stopped·연속 실패를 일부러 뺐다 — D-3 없이 영속하면 stopped=true가
-- 영구히 굳어 차단된 사이트가 다시 못 돈다. D-3이 2026-07-24 "설정/DB 값 수동 수정"으로 확정됐다:
-- 재개는 운영자가 이 테이블의 행을 직접 UPDATE하는 것이고, 별도 명령·API는 없다.
--
-- last_successful_poll_at은 이제 nullable이다 — 한 번도 성공한 적 없이(첫 시도부터 BLOCKED)
-- 커서가 생기는 사이트가 있을 수 있고, 그 사실을 가짜 시각으로 덮으면 안 된다. core의 관측시계
-- (ObservationClock.earliestSuccessfulPoll, min() 집계)는 SQL MIN이 NULL을 자연히 걸러내므로
-- 이 완화에 영향받지 않는다 — 별도 코드 변경 없이 그대로 옳다.

alter table site_poll_state
    alter column last_successful_poll_at drop not null,
    add column consecutive_failures integer not null default 0,
    add column next_attempt_at      timestamptz,
    add column stopped              boolean not null default false;

comment on column site_poll_state.stopped is
    '차단(403/429) 감지로 자동 중지됨. 재개 = 운영자가 이 값을 false로, next_attempt_at을 null로,
     consecutive_failures를 0으로 직접 UPDATE(decisions-needed D-3). 별도 명령·API 없음.';
comment on column site_poll_state.consecutive_failures is
    '연속 일시장애(TRANSIENT) 횟수. 성공하면 0으로 리셋(collector policy.advance).';
comment on column site_poll_state.next_attempt_at is
    '다음 폴링 예정 시각(백오프 반영). null이면 즉시 due.';
