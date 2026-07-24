-- DIG-04 ⑤ "N회째 미확인" — 전역 스톡(review_queue_item)이 다이제스트에 몇 번 실렸는지 센다.
--
-- 발송 시 +1(SendDigestUseCase, 전 분할 성공 시에만 — DIG-02 원자성과 같은 규칙). 쓰기는
-- ReviewQueueItemEntity가 아니라 벌크 UPDATE로 한다 — 엔티티에 매핑하면 occurrences 등 다른 필드를
-- 바꾸는 다른 writer(IngestDealsUseCase 등)의 save()가 이 값을 건드릴 표면이 늘어난다(core-java 규칙:
-- 미매핑 컬럼은 delete+insert에서만 위험 — 여기는 애초에 벌크 UPDATE라 문제가 되지 않지만, 매핑을
-- 안 하는 편이 "이 컬럼은 발송 배선만 건드린다"는 계약을 코드로도 드러낸다).
alter table review_queue_item
    add column digest_appearances int not null default 0;
