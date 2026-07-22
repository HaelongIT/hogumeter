-- V11 역전 — used_search.listings_folded_through 제거(rollback-drill이 V 전진 → R 역순 후진을 검증한다, REL-05).
alter table used_search
    drop column if exists listings_folded_through;
