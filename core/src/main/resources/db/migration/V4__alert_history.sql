-- V4: 알림 이력(AL-03 후속 알림, Q-67). 정본: docs/91 Q-67.
-- 후속 알림(검증·가격변화·종료)은 **이미 첫 알림이 나간 딜에만** 보낸다 — 처음부터 알림 대상이 아니었던
-- 딜은 전이해도 후속을 만들지 않는다(FollowUpEvaluator). 그 판정을 하려면 "무엇에 알림이 나갔나"를
-- 저장해야 한다. 그게 없어 FollowUpEvaluator가 GREEN인 채 소비처 0이었다. 롤백: R4__alert_history_rollback.sql

create table deal_alert (
    id            bigserial   primary key,
    deal_event_id bigint      not null references deal_event (id),
    kind          text        not null
                  check (kind in ('FIRST', 'VERIFIED', 'PRICE_CHANGED', 'ENDED')),
    sent_at       timestamptz not null default now(),
    unique (deal_event_id, kind)   -- 종류별 1회(멱등) — 매 틱 도는 후속이 같은 알림을 재발송하지 않는다
);

create index idx_deal_alert_deal on deal_alert (deal_event_id);
