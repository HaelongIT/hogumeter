-- PUR-04 성적표 — 관찰이 끝난 구매의 "호구였나" 최종 판정을 담는다.
-- 발급 유스케이스(IssuePendingReportCardsUseCase)가 REPORT_PENDING 구매마다 한 번 발급하고 CLOSED로 전이한다.
-- ReportCardCalculator(순수 도메인, 그전까지 프로덕션 호출자 0 — docs/91 Q-62)가 여기 값을 계산한다.
--
-- 정직성(절대 원칙 1·6): 유머 등급·라벨 없음. n=0·UNOBSERVED면 통계 필드(percentile·lowest_opportunity)는 null이다.
-- 재발급 없음(ReportIssueGate) = purchase_id 유니크. 발급은 quiet(관통 알림 없음)이라 텔레그램 토큰과 무관하다.
create table report_card (
    id                 bigserial primary key,
    purchase_id        bigint      not null unique references purchase (id),
    unobserved         boolean     not null,                 -- purchasedAt < observedFrom(관측 시작 이전 구매)
    n                  int         not null,                 -- 관찰기간·관측시작 이후 pricingSet 딜 수
    cheaper_count      int         not null,                 -- 내 구매가보다 싼 딜 수 X(동가 미포함)
    percentile         numeric(4, 3),                        -- Y = X/n (0~1). n=0·UNOBSERVED면 null
    lowest_opportunity bigint,                               -- 기간 내 최저 기회(min priceMin). null 가능
    paid_price         bigint      not null,                 -- as-paid 실지불가
    paid_gap           bigint,                               -- 구매가 − 동결 기준가(PUR-02). 기준가 부재 시 null
    issued_at          timestamptz not null
);
