-- WATCH(docs/17) — 딜 보관함. WatchItem = dealEventId + anchorPostId(이력) + 메모 + 결말 상태.
--
-- 유일성 = "딜당 활성 핀 1개"(부분 유니크 인덱스 — state='ACTIVE'인 행만 충돌). 결말(BOUGHT/MISSED/
-- DROPPED)에 닿은 행은 종착이라 그대로 두고, 재핀은 같은 deal_event_id로 새 행을 만든다 — 이력은
-- deal_event_id로 자연히 이어진다(옛 행을 지우거나 UPDATE하지 않는다).
create table watch_item (
    id             bigserial primary key,
    deal_event_id  bigint      not null references deal_event (id),
    anchor_post_id bigint references raw_deal_post (id),
    note           text,
    state          text        not null default 'ACTIVE'
                   check (state in ('ACTIVE', 'BOUGHT', 'MISSED', 'DROPPED')),
    created_at     timestamptz not null default now(),
    resolved_at    timestamptz
);

create unique index uq_watch_item_active_deal on watch_item (deal_event_id) where state = 'ACTIVE';
