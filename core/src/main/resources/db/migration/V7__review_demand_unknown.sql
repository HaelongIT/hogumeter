-- Q-66 ① E: 값 미상 딜을 사람에게 보인다(확정본 §41).
--
-- 분리(SPLIT) 제품에서 제목으로 수요축 값을 판별하지 못한 딜은 기준가 표본에서 빠진다(V6·demandScope).
-- 빠지는 것 자체는 정직하지만, **큐에 뜨지 않으면 사람이 볼 수 없다** — §41은 "미상 버킷은 기준가 계산
-- 제외 + 승격 큐에서 사람이 분류"라고 못박는다. 지금은 앞 절반만 있었다.
--
-- 새 유형 DEMAND_UNKNOWN: "이 딜이 무슨 색인지 모른다"를 사람이 보고 분류할 대기 항목이다.
-- UNCLASSIFIED(제품·variant 자체가 미상)와 다르다 — 이쪽은 variant는 확정됐고 수요축 값만 모른다.
alter table review_queue_item
    drop constraint review_queue_item_type_check;
alter table review_queue_item
    add constraint review_queue_item_type_check
    check (type in ('UNCLASSIFIED', 'OUTLIER_LOWER', 'KEYWORD_SUGGEST', 'DEMAND_UNKNOWN'));
