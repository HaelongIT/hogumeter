-- V6 역: 딜의 수요축 값 컬럼·인덱스 제거(Q-66 ①).
drop index if exists idx_deal_event_variant_demand;
alter table deal_event
    drop column if exists demand_axis_value;
