-- PRI ②축소(docs/19, DN-P) — Product 단위 우선순위 순번 + 수동 완료(중고·장외 구매 이탈용) 손잡이.
--
-- priority_rank: 유일 순번(부분 유니크 인덱스 — null은 "미지정"이라 서로 충돌하지 않는다, dedup_key와
-- 같은 수법). manually_completed: 취소 가능한 손잡이라 boolean(기본 false) — 알림은 계속 유지된다.
alter table product
    add column priority_rank      int,
    add column manually_completed boolean not null default false;

create unique index uq_product_priority_rank on product (priority_rank) where priority_rank is not null;
