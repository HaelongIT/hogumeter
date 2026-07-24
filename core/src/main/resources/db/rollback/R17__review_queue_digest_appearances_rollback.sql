-- R17: V17 역. (REL-05 rollback-drill이 전진→역순 후진을 검증한다.)
alter table review_queue_item
    drop column digest_appearances;
