-- V5 역: 미상 큐 dedup 컬럼·인덱스 제거(Q-27 ④).
drop index if exists uq_review_queue_dedup;
alter table review_queue_item
    drop column if exists dedup_key,
    drop column if exists last_seen_at,
    drop column if exists occurrences;
