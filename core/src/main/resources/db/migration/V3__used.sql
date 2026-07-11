-- V3: 중고(USED, M2) 스키마. 정본 방향: docs/used/02-data-model.md.
-- V2 슬롯은 purchase가 점유했다(docs/91 Q-4) → used는 V3+. 롤백: db/rollback/R3__used_rollback.sql
-- bonus_groups 저장 = 그룹 행 + 키워드 text[] 배열(보수적 기본값, JSONB 금지 준수; 잠정 docs/91 Q-71).

-- ── 중고 검색 (product 종속, USED-01) ────────────────────────────────

create table used_search (
    id                bigserial   primary key,
    product_id        bigint      not null references product (id),
    platform          text        not null default 'BUNJANG'
                      check (platform in ('BUNJANG')),
    required_keywords text[]      not null default '{}',   -- AND (제품 정체성)
    exclude_keywords  text[]      not null default '{}',   -- NOT (검색별; 전역 기본은 global_setting)
    target_price      bigint,                              -- 이하면 알림 (선택)
    poll_interval_min int         not null default 10,     -- 하한 10 (SiteKind.MARKETPLACE)
    created_at        timestamptz not null default now()
);

-- 보너스 그룹 (OR 그룹, 모드별). 그룹=행 + 키워드 배열(동의어 나열, 그룹 내 OR).
create table used_search_bonus_group (
    id             bigserial primary key,
    used_search_id bigint not null references used_search (id),
    mode           text   not null check (mode in ('SORT', 'TRIGGER')),
    keywords       text[] not null default '{}'
);

create index idx_used_bonus_group_search on used_search_bonus_group (used_search_id);

-- ── collector 적재 계약 테이블 (insert-only 목록 스냅샷, USED-02) ─────

create table used_listing_observation (
    id             bigserial   primary key,
    used_search_id bigint      not null references used_search (id),
    listing_id     text        not null,                   -- 플랫폼 매물 ID (자연키)
    title          text        not null,
    price          bigint      not null,
    observed_at    timestamptz not null,
    raw            jsonb                                    -- 크롤링 원본 (개인정보 배제, SEC-07)
);

create index idx_used_obs_search_observed on used_listing_observation (used_search_id, observed_at);

-- ── 매물 (목록 diff가 도출·갱신하는 생애주기 엔티티, USED-02) ────────

create table listing (
    id             bigserial   primary key,
    used_search_id bigint      not null references used_search (id),
    listing_id     text        not null,                   -- 자연키 (끌올 dedupe·소실 판정 기준)
    title          text        not null,
    price          bigint      not null,
    status         text        not null default 'ACTIVE'
                   check (status in ('ACTIVE', 'SOLD', 'REMOVED')),
    promoted       boolean     not null default false,     -- 알림 승격 (후속 알림 한정 조건)
    detail_fetched boolean     not null default false,     -- 승격 시 1회 상세 fetch 완료
    first_seen     timestamptz not null,
    last_seen      timestamptz not null,
    unique (used_search_id, listing_id)                    -- 자연키 dedupe
);

-- ── 메모·축 (EAV — JSONB 금지, docs/used/02, USED-05) ────────────────

create table listing_note (
    id         bigserial   primary key,
    listing_id bigint      not null references listing (id),
    body       text        not null,                       -- 자유 메모 (글에 없는 관찰 포함)
    created_at timestamptz not null default now()
);

create table comparison_axis (
    id         bigserial primary key,
    product_id bigint not null references product (id),    -- 비교축은 제품 단위 정의
    name       text   not null,
    unique (product_id, name)
);

create table listing_axis_value (
    id         bigserial primary key,
    listing_id bigint not null references listing (id),
    axis_id    bigint not null references comparison_axis (id),
    value      text   not null,                            -- 메모→축 승격 값
    unique (listing_id, axis_id)
);
