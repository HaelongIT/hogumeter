-- V14: 쿠팡 크롬 확장이 보낸 가격 관측(CMP-02, docs/13 · SEC-04).
-- 롤백: db/rollback/R14__coupang_price_observation_rollback.sql
--
-- **서버는 쿠팡에 절대 접근하지 않는다**(docs/13 CMP-02, docs/90 §확정). 사용자가 브라우저에서 쿠팡
-- 상품 페이지를 열면 확장이 DOM을 읽어 이 테이블로 보낸다 — core는 받기만 한다(SEC-04 고정 토큰 인증).
--
-- wow_price가 NULL인 것은 "0원"이 아니라 "와우회원가가 없다"(비회원·미표시)는 사실이다 —
-- "값 없음"을 sentinel 0으로 흘리면 CMP-01 비교 화면이 와우가 0원짜리 초특가로 잘못 그린다.

create table coupang_price_observation (
    id            bigserial   primary key,
    variant_id    bigint      not null references variant (id),
    regular_price bigint      not null,
    wow_price     bigint,
    shipping_fee  bigint,
    url           text        not null,
    observed_at   timestamptz not null,
    raw           jsonb
);

create index idx_coupang_price_variant_observed
    on coupang_price_observation (variant_id, observed_at desc);

comment on column coupang_price_observation.wow_price is
    '와우회원가. NULL = 비회원이거나 표시되지 않음(0원이 아니다).';
comment on column coupang_price_observation.shipping_fee is
    'NULL = 확장이 못 읽었다(모름). 무료배송이 확인되면 0으로 기록한다.';
comment on column coupang_price_observation.raw is
    '확장이 보낸 원본 중 허용 필드만(SEC-07, 개인정보 최소화 — 판매자 식별자 등 배제).';
