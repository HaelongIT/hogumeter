-- V23: raw_deal_post.origin — REG-04 백필 배선의 선결 스키마(docs/91 Q-87).
-- deal_event.origin(V1)과 같은 값 집합. 기본 'LIVE'라 기존 행·현재 collector 쓰기는 영향 없음(additive).
-- IngestDealsUseCase.candidateFrom이 이 값을 deal_event.origin으로 그대로 옮긴다(과거엔 하드코딩 LIVE).
alter table raw_deal_post
    add column origin text not null default 'LIVE' check (origin in ('LIVE', 'BACKFILL'));
