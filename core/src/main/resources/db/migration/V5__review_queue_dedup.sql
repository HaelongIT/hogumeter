-- Q-27 ④: 미상 큐가 매 틱 같은 근거를 새 행으로 쌓았다.
--
-- findUnprocessed()는 deal_event_source 링크가 없는 원문을 미처리로 본다. 그런데 매칭에 실패한 원문
-- (CANDIDATE·UNKNOWN)은 딜을 만들지 않아 링크도 안 생긴다 → 매 틱 다시 스캔되어 다시 큐에 들어갔다
-- (운영 60초 주기면 원문 하나당 하루 1,440행). 읽기 모델(GetReviewQueueUseCase)은 (type,payload)로 접어
-- occurrences로 세 왔지만, 저장은 무한히 늘었고 Q-15(승격·기각)는 "하나를 처리해도 N-1개가 남는다"는
-- 이유로 막혀 있었다.
--
-- 이제 같은 근거를 한 행으로 접고, 재적재는 세어서 드러낸다(조용히 지우면 결함이 사라진 것처럼 보인다).
--   - occurrences  : 이 근거가 큐에 들어간 횟수. 1보다 크면 "재처리 멱등이 없다"는 증거다.
--   - last_seen_at : 마지막 재적재 시각(created_at = 최초). 그 구간이 곧 결함의 나이다(web seenLine).
--   - dedup_key    : 같은 근거를 접는 키. 쓰기 쪽이 유형별로 만든다(UNCLASSIFIED=원문 id, OUTLIER_LOWER=딜 id).
--
-- dedup_key는 nullable + unique다: 기존 행(키 없음)은 null이라 서로 충돌하지 않고(postgres는 null을 서로
-- 다르게 본다), 새 행만 키로 upsert되어 접힌다. 백필하지 않는다 — 아직 운영 데이터가 없다(M1).
alter table review_queue_item
    add column occurrences  int         not null default 1,
    add column last_seen_at timestamptz not null default now(),
    add column dedup_key    text;

create unique index uq_review_queue_dedup on review_queue_item (dedup_key);
