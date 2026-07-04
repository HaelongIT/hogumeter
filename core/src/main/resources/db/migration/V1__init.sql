-- V1: 신품 코어 루프(M1 = REG + BM + AL) 스키마.
-- 정본: docs/02-domain-model.md, docs/benchmark/02-data-model.md.
-- used(중고, M2) 테이블은 V2로 이월 — docs/91 Q-4. 롤백: db/rollback/R1__init_rollback.sql

-- ── 제품·축·별칭 (REG 소유) ──────────────────────────────────────────

create table product (
    id               bigserial primary key,
    name             text        not null,
    category         text,
    demand_axis_mode text        not null default 'GROUPED'
                     check (demand_axis_mode in ('GROUPED', 'SPLIT')),
    created_at       timestamptz not null default now()
);

create table product_axis (
    id             bigserial primary key,
    product_id     bigint not null references product (id),
    axis_type      text   not null check (axis_type in ('PRICE', 'DEMAND')),
    name           text   not null,
    allowed_values text[] not null,
    unique (product_id, axis_type, name)
);

create table variant (
    id                bigserial primary key,
    product_id        bigint not null references product (id),
    price_axis_values jsonb  not null,
    label             text   not null,
    unique (product_id, label)
);

create table alias_dictionary (
    id         bigserial primary key,
    product_id bigint references product (id),  -- null = 전역 별칭
    alias      text not null,
    unique nulls not distinct (product_id, alias)
);

-- ── 수집 원문 (collector가 쓰는 유일한 계약 테이블) ─────────────────

create table raw_deal_post (
    id             bigserial primary key,
    site           text        not null,
    post_id        text        not null,
    url            text        not null,
    title          text        not null,
    body_text      text,
    headline_price bigint,
    posted_at      timestamptz,
    captured_at    timestamptz not null,
    reaction_score numeric,
    status         text        not null default 'ACTIVE'
                   check (status in ('ACTIVE', 'SOLD_OUT', 'DELETED')),
    raw            jsonb,                       -- 크롤링 원본 보관 전용
    unique (site, post_id)                      -- 멱등 수집 (REL-01)
);

-- ── 병합 딜 (BM 소유) ───────────────────────────────────────────────

create table deal_event (
    id                   bigserial primary key,
    variant_id           bigint references variant (id),  -- null = 미상
    product_candidates   bigint[],                        -- 미상 시 후보군
    unclassified         boolean     not null default false,
    price_first          bigint      not null,            -- 대표가 (분포 입력)
    price_min            bigint      not null,
    price_max            bigint      not null,
    price_last           bigint      not null,
    shipping             bigint      not null default 0,
    base_price           bigint,
    applied_conditions   text[],
    confidence           numeric(3, 2),
    origin               text        not null check (origin in ('LIVE', 'BACKFILL')),
    cross_verified       boolean     not null default false,
    outlier_flag         text        not null default 'NONE'
                         check (outlier_flag in ('NONE', 'UPPER', 'LOWER')),
    permanently_excluded boolean     not null default false,  -- 사기 기각 시 영구 제외
    status               text        not null default 'NEW'
                         check (status in ('NEW', 'ACTIVE', 'VERIFIED', 'ENDED')),
    first_seen           timestamptz not null,
    last_seen            timestamptz not null
);

create index idx_deal_event_variant_seen on deal_event (variant_id, first_seen);

create table deal_event_source (
    id               bigserial primary key,
    deal_event_id    bigint not null references deal_event (id),
    raw_deal_post_id bigint not null references raw_deal_post (id),
    site             text   not null,
    unique (deal_event_id, raw_deal_post_id)
);

-- ── 현재가·알림 정책·승격 큐 ────────────────────────────────────────

create table price_history (
    id         bigserial primary key,
    variant_id bigint      not null references variant (id),
    source     text        not null default 'NAVER',
    price      bigint      not null,
    fetched_at timestamptz not null
);

create index idx_price_history_variant on price_history (variant_id, fetched_at desc);

create table alert_policy (
    id                 bigserial primary key,
    variant_id         bigint not null unique references variant (id),
    target_price       bigint,                          -- 선택 (기본 미설정)
    period_months      int    not null,                 -- 기간 P (제품별 사용자 설정)
    k_display          int    not null default 5 check (k_display between 3 and 10),
    exclude_keywords   text[] not null default '{}',    -- 제품별 추가분 (전역은 global_setting)
    quiet_hours_start  smallint check (quiet_hours_start between 0 and 23),
    quiet_hours_end    smallint check (quiet_hours_end between 0 and 23),
    demand_axis_filter jsonb
);

create table review_queue_item (
    id          bigserial primary key,
    type        text        not null
                check (type in ('UNCLASSIFIED', 'OUTLIER_LOWER', 'KEYWORD_SUGGEST')),
    payload     jsonb       not null,
    status      text        not null default 'PENDING'
                check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    channel     text check (channel in ('TELEGRAM', 'WEB')),
    created_at  timestamptz not null default now(),
    resolved_at timestamptz
);

create index idx_review_queue_status on review_queue_item (status, created_at);

-- ── 전역 설정 (설정값 장부 docs/90 §8 — 전역 제외 키워드 기본셋 등) ──

create table global_setting (
    key        text        primary key,
    value      jsonb       not null,
    updated_at timestamptz not null default now()
);
