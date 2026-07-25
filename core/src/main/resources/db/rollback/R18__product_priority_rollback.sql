-- R18: V18 역. (REL-05 rollback-drill이 전진→역순 후진을 검증한다.)
drop index if exists uq_product_priority_rank;
alter table product
    drop column priority_rank,
    drop column manually_completed;
