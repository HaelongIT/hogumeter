-- V15 역전 — 컬럼 셋을 제거하고 NOT NULL을 복원한다.
-- last_successful_poll_at을 다시 not null로 만들려면 기존 NULL 행이 없어야 한다 — 롤백은
-- 개발/드릴 컨텍스트(빈 DB 또는 일회용 컨테이너)에서만 돈다는 전제(REL-05).
alter table site_poll_state
    drop column stopped,
    drop column next_attempt_at,
    drop column consecutive_failures,
    alter column last_successful_poll_at set not null;
