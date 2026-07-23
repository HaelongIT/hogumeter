-- V13 역전 — url 컬럼 제거(rollback-drill이 V 전진 → R 역순 후진을 검증한다, REL-05).
alter table listing
    drop column if exists url;

alter table used_listing_observation
    drop column if exists url;
