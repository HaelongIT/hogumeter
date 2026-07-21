-- V9: 사후학습(Q-22, BM-07) — 사용자가 [무시]한 딜을 적어 둔다. 정본: docs/91 Q-22.
-- 알림에서 [🔕무시]를 누르면 이 딜을 노이즈로 기록한다. 같은 variant의 무시 제목들에서 빈출 토큰을
-- KeywordSuggester가 제외 키워드 후보로 뽑아 KEYWORD_SUGGEST 큐를 만든다("판단은 사람" — 자동 반영은 없다).
-- 딜당 1건(멱등) — 같은 알림을 두 번 눌러도 한 번만. title은 학습 입력이라 그 시점 값을 그대로 박제한다.
-- 롤백: R9__deal_ignore_rollback.sql

create table deal_ignore (
    id            bigserial   primary key,
    deal_event_id bigint      not null references deal_event (id),
    variant_id    bigint      not null,
    title         text        not null,
    ignored_at    timestamptz not null default now(),
    unique (deal_event_id)
);

create index idx_deal_ignore_variant on deal_ignore (variant_id);
