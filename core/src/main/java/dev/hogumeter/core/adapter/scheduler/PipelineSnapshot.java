package dev.hogumeter.core.adapter.scheduler;

/**
 * 한 시점의 파이프라인 상태(전부 절대 수). 틱 전후로 한 번씩 찍어 차이를 낸다.
 *
 * @param rawPosts 수집된 원문 총수
 * @param linkedSources {@code deal_event_source} 링크 수 — 원문 하나가 딜에 붙을 때마다 1
 * @param dealEvents 딜 총수
 * @param reviewQueue 승격 큐 적체
 * @param endedDeals 종료된 딜 수
 * @param unprocessed 아직 딜에 붙지 않은 원문 수. {@code rawPosts - linkedSources}로 근사하지 않는다 —
 *        {@code unique (deal_event_id, raw_deal_post_id)}는 한 원문이 두 딜에 붙는 것을 막지 않으므로
 *        그 뺄셈은 가정이다. 정확한 값을 쓴다(1인용 규모라 스캔 비용은 무의미하다).
 * @param reportPendingPurchases 관찰이 끝나 성적 집계를 기다리는 구매 수. <b>OBSERVING을 세지 않는 이유</b>:
 *        틱 도중 REST로 새 구매가 들어오면 OBSERVING 차이가 오염된다. REPORT_PENDING은 스케줄러만 늘린다.
 * @param conditionalDeals 조건부 가격 태그가 붙은 딜 수(BM-02 AC-2). 골든 실측으로 뽐뿌 9.5% · 펨코 15%가
 *        조건부다 — "N카드 할인 시 890,000"의 890,000은 분포에 그대로 들어가지만(as-posted, 역산 금지)
 *        <b>조건부라는 사실은 남아야 한다.</b> 이 수가 0에 붙어 있으면 태그가 어딘가에서 유실되고 있다.
 */
public record PipelineSnapshot(
		long rawPosts,
		long linkedSources,
		long dealEvents,
		long reviewQueue,
		long endedDeals,
		long unprocessed,
		long reportPendingPurchases,
		long conditionalDeals) {
}
