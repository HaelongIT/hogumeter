package dev.hogumeter.core.adapter.scheduler;

/**
 * OBS-02 핵심 카운터 — 한 틱이 무엇을 했는가. 절대 수가 아니라 <b>차이</b>다.
 *
 * <p>{@code merged}는 직접 셀 수 없어 유도한다: 원문이 딜에 붙으면 링크가 1 늘고, 그게 <b>새 딜</b>이면
 * 딜도 1 는다. 링크는 늘었는데 딜이 안 늘었으면 기존 딜에 흡수된 것이다(BM-04 병합).
 *
 * <p>{@code pending}은 이번 틱 뒤에도 딜에 붙지 않은 원문 수다. 가격 없음(BM-02 AC-3)·매칭 거절은
 * 링크를 만들지 않으므로 <b>다음 틱에도 다시 스캔된다</b>(docs/91 Q-27 ④). 이 값이 단조 증가하면
 * 파이프라인이 도는 척하며 아무것도 처리하지 않는 것이다 — collector의 "성공했는데 0건"과 같은 신호다.
 *
 * <p>{@code conditionalTotal}·{@code shippingUnknownTotal}은 차이가 아니라 <b>절대 수</b>다. 둘은 다른
 * 사실을 말한다: 조건부 딜은 as-posted로 옳은 값이고(BM-02 AC-2), 그중 <b>배송비 미상</b> 딜만이 실제보다
 * 낮은 값으로 기준가를 끈다. 합쳐 세면 아무것도 말하지 않는다. 지금 이 카운터가 표본 오염률을 보는
 * <b>유일한 창</b>이다(화면·알림 표시와 표본 제외는 미구현, docs/91 Q-46).
 */
public record PipelineTickReport(
		long postsLinked,
		long dealsCreated,
		long merged,
		long queued,
		long ended,
		long purchasesExpired,
		long conditionsTagged,
		long conditionalTotal,
		long shippingUnknownTotal,
		long pending,
		long rawTotal) {

	public static PipelineTickReport between(PipelineSnapshot before, PipelineSnapshot after) {
		long postsLinked = after.linkedSources() - before.linkedSources();
		long dealsCreated = after.dealEvents() - before.dealEvents();
		return new PipelineTickReport(
				postsLinked,
				dealsCreated,
				postsLinked - dealsCreated,
				after.reviewQueue() - before.reviewQueue(),
				after.endedDeals() - before.endedDeals(),
				after.reportPendingPurchases() - before.reportPendingPurchases(),
				after.conditionalDeals() - before.conditionalDeals(),
				after.conditionalDeals(),
				after.shippingUnknownDeals(),
				after.unprocessed(),
				after.rawPosts());
	}

	/** 한 줄 요약. 0을 생략하지 않는다 — "성공했는데 0건"이 사라지면 드리프트를 못 본다. */
	@Override
	public String toString() {
		return "postsLinked=" + postsLinked
				+ " dealsCreated=" + dealsCreated
				+ " merged=" + merged
				+ " queued=" + queued
				+ " ended=" + ended
				+ " purchasesExpired=" + purchasesExpired
				+ " conditionsTagged=" + conditionsTagged
				+ " conditionalTotal=" + conditionalTotal
				+ " shippingUnknownTotal=" + shippingUnknownTotal
				+ " pending=" + pending
				+ " rawTotal=" + rawTotal;
	}
}
