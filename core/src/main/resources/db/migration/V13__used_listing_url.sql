-- V13: 중고 매물의 **원문 링크**를 관측·매물에 담는다 (USED-03 알림의 전제).
-- 롤백: db/rollback/R13__used_listing_url_rollback.sql
--
-- 왜 필요한가: 절대 원칙 2·6 — 시스템은 판단하지 않고 **원문으로 넘긴다**. 링크 없는 중고 알림은
-- "번개 매물 1234567이 떴다"까지만 말하고 사람이 다시 검색해야 한다. 파서는 이미 URL을 만들고
-- 있었는데(`https://m.bunjang.co.kr/products/{pid}`) 적재 경로에서 버려지고 있었다.
--
-- **core가 URL을 조립하지 않는다.** 플랫폼별 URL 형태는 파서가 아는 어휘다 — 두 모듈이 각자
-- 해석하면 한쪽이 조용히 틀린다(같은 이유로 배송비 어휘를 `pipeline/price.py` 한 곳에 모았다).
--
-- nullable: V13 이전에 적재된 관측에는 URL이 없다. 기본값으로 지어내지 않는다("값 없음"은 값이 아니다).

alter table used_listing_observation
    add column url text;

alter table listing
    add column url text;

comment on column used_listing_observation.url is
    '파서가 만든 원문 링크. NULL = V13 이전 관측이거나 파서가 URL을 못 만든 경우.';
comment on column listing.url is
    '이 매물의 원문 링크(관측에서 옮겨온다). 알림·화면이 사람을 원문으로 보낸다.';
