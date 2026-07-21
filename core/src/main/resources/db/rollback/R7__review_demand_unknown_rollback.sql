-- V7 역: review_queue_item.type CHECK를 DEMAND_UNKNOWN 없는 3종으로 되돌린다(Q-66 ① E).
alter table review_queue_item
    drop constraint review_queue_item_type_check;
alter table review_queue_item
    add constraint review_queue_item_type_check
    check (type in ('UNCLASSIFIED', 'OUTLIER_LOWER', 'KEYWORD_SUGGEST'));
