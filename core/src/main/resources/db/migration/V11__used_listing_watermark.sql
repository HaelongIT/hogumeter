-- V11: 중고 목록 스냅샷을 **어디까지 접었는지**를 검색별로 명시한다(USED-02).
-- 롤백: db/rollback/R11__used_listing_watermark_rollback.sql
--
-- 왜 컬럼인가: `max(listing.last_seen)`에서 파생할 수도 있으나, **소실만 있는 배치**(모든 매물이
-- 사라진 스냅샷)는 어떤 listing의 last_seen도 올리지 못해 워터마크가 제자리에 머문다. 그러면 같은
-- 배치를 영원히 다시 접는다. 파생값은 "처리했다"를 표현할 수 없다 — 사실을 직접 적는다.
--
-- NULL = 아직 한 배치도 접지 않았다(진짜 상태이지 sentinel이 아니다). 기본값을 주지 않는 이유다.

alter table used_search
    add column listings_folded_through timestamptz;

comment on column used_search.listings_folded_through is
    '이 검색의 used_listing_observation을 이 시각(포함)까지 listing에 접었다. NULL = 미처리.';
