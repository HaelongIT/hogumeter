-- V16: DIGEST(docs/18) 저장물 — variant당 마지막 발송 상태(DIG-02).
-- 롤백: db/rollback/R16__digest_state_rollback.sql
--
-- 행이 없다 = 이 variant로 다이제스트를 한 번도 성공 발송한 적 없다(첫 창은 활성 시각부터,
-- DIG-03 DigestWindow.of). **발송 성공 후에만 갱신**(REL-03 원자성) — 실패한 발송이 이 행을
-- 앞서가면 다음 창이 그 사이 실제 변화를 건너뛴다.
--
-- stored_context는 working-area/2nd-plan-intake.md#98 "관찰 문맥(Purchase별 1줄)"이 그 variant의
-- PUR 관찰 문맥(ObservationContext.mode — ACTIVE_DEAL/NO_ACTIVE_DEAL/REPORT_PENDING)을 뜻한다.
-- stored_basis_mode는 그 variant가 속한 product의 DemandAxisMode(GROUPED/SPLIT) — "SPLIT은 목록
-- 셀 집계로 variant당 1벌"이 이 값과 짝이다. 둘 다 색 자체를 바꾸지 않는 "전환 억제 신호"라 별도
-- 저장한다(DigestRules.isReportableTransition은 색만 비교한다).

create table digest_state (
    variant_id        bigint primary key references variant (id),
    last_sent_at      timestamptz,
    stored_color      text,
    stored_context    text,
    stored_basis_mode text,
    updated_at        timestamptz not null default now()
);

comment on table digest_state is
    'DIGEST(docs/18) variant당 마지막 발송 상태. 행 부재 = 한 번도 발송 안 함(DIG-03 창 시작=활성 시각).';
