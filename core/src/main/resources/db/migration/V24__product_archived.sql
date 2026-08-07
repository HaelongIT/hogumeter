-- V24: product.archived — 확정본(docs/90 §10) "제품 노화·만료 처리"가 지금까지 구현·추적 어느
-- 쪽도 안 돼 있었다(docs/91 Q-91). 자동 시간 기반 만료는 임계값이 정책 결정이라 이번엔 열지 않고,
-- 사람이 직접 켜고 끄는 수동 보관만 연다 — 표시 손잡이(절대 원칙 4), 매칭·폴링 로직은 안 건드린다.
alter table product
    add column archived boolean not null default false;
