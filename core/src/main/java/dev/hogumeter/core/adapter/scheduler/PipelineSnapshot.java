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
 */
public record PipelineSnapshot(
		long rawPosts,
		long linkedSources,
		long dealEvents,
		long reviewQueue,
		long endedDeals,
		long unprocessed) {
}
