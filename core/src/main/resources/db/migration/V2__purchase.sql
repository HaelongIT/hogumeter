-- V2: 구매 기록·관찰 모드·성적표 (PUR, docs/15) — 신품 한정.
-- variant 정책을 점유하지 않음 → variant당 복수 Purchase 공존(독립 관찰).

create table purchase (
    id                   bigserial   primary key,
    variant_id           bigint      not null references variant (id),
    demand_axis_value    text,                              -- SPLIT 필수·GROUPED 선택 (검증은 후속)
    paid_price           bigint      not null,              -- as-paid 실지불가
    purchased_at         timestamptz not null,              -- 발생 시각(날짜만이면 23:59 KST는 입력 계층)
    observation_days     int         not null default 90,   -- 관찰 기간(자동확장 없음)
    linked_deal_event_id bigint      references deal_event (id),
    state                text        not null default 'OBSERVING'
                         check (state in ('OBSERVING', 'REPORT_PENDING', 'CLOSED', 'ARCHIVED')),
    -- PUR-02 스냅샷(구매 시점 as-of 동결) — 통계 없으면 null(정직성 도메인 계약)
    snap_benchmark_price bigint,
    snap_tier            text        check (snap_tier in ('SUFFICIENT', 'SPARSE', 'NONE')),
    snap_n               int         not null default 0,
    snap_m               int         not null default 0,
    snap_sparse_lowest   bigint,
    snap_paid_gap        bigint,
    snap_basis           text,
    snap_unobserved      boolean     not null default false,
    created_at           timestamptz not null default now()
);

create index idx_purchase_variant on purchase (variant_id, state);
