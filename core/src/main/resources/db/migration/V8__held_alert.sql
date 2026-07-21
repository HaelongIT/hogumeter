-- V8: 방해금지 보류 알림 큐(AL-04/07, Q-20 ②). 정본: docs/91 Q-20 ②.
-- 방해금지(quiet hours)로 HOLD된 첫 알림은 그동안 담을 곳이 없어 유실됐다(플러시 부재). 이 표가 그 큐다 —
-- 보류된 딜을 적어 두고, 방해금지가 끝난 틱에 **재평가**해 보낸다(FlushHeldAlertsUseCase). 재평가라 저장된
-- 본문이 아니라 발송 시점의 현재 상태로 판정한다(AL-07): 더는 자격 없으면 드롭, 살아 있으면 최신가로 발송.
-- 딜당 1건(멱등) — 같은 딜이 여러 틱 연속 보류돼도 한 번만 재평가 대상. 롤백: R8__held_alert_rollback.sql

create table held_alert (
    id            bigserial   primary key,
    deal_event_id bigint      not null references deal_event (id),
    variant_id    bigint      not null,
    held_at       timestamptz not null default now(),
    unique (deal_event_id)
);

create index idx_held_alert_variant on held_alert (variant_id);
