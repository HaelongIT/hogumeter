-- V12: 사이트별 **마지막 성공 폴링 시각**을 영속한다 (docs/03 3-2 관측시계, SIG-02 신선도, Q-34 (b)).
-- 롤백: db/rollback/R12__site_poll_state_rollback.sql
--
-- 왜 필요한가: staleness = (소스 사이트 마지막 성공 폴링) − lastEvidenceAt 이 정본인데, core는 그 값을
-- 어디서도 읽을 수 없어 `clock.instant()`(벽시계)로 대신하고 있었다. 그러면 **수집이 멈춘 동안 딜이
-- 늙는 것처럼 보여** 신호등이 "딜 없음"으로 강등된다 — docs/03이 명시적으로 막으려던 "무지를 부재로
-- 오독"이 그대로 일어난다. collector가 성공한 폴링만 여기 적고, core가 읽는다.
--
-- **성공만 적는다.** 연속 실패 횟수·`stopped` 플래그(REL-03 폴링 커서)는 여기 두지 않는다 — 그건
-- Q-59이고 D-3(차단 사이트 재개 경로)이 정해지기 전에 영속하면 `stopped=true`가 영구히 굳는다.
-- 실패를 적지 않는 테이블은 그 위험이 없다: 최악이 "값이 안 늘어난다"(= 시계 정지, 의도된 동작).

create table site_poll_state (
    site                    text primary key,
    last_successful_poll_at timestamptz not null,
    updated_at              timestamptz not null default now()
);

comment on table site_poll_state is
    '사이트별 마지막 성공 폴링 시각. collector가 쓰고 core(신선도)가 읽는다. 실패 상태는 담지 않는다(Q-59).';
comment on column site_poll_state.site is
    'collector의 SiteSpec.name — 게시판(ppomppu 등)과 마켓(bunjang#12)이 같은 이름공간을 쓴다.';
