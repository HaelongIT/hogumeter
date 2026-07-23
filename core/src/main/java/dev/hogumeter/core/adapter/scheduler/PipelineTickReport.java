package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.application.FoldUsedListingsUseCase.FoldReport;
import dev.hogumeter.core.application.IngestReport;

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
 *
 * <p>{@code ingest}는 이번 틱 수집의 매칭 tier 분포·첫 알림 발송 수다(OBS-02, Q-57 ②③). 스냅샷 차이로는
 * 셀 수 없다 — CANDIDATE·REJECTED는 딜을 만들지 않아 링크·딜 수에 흔적이 없다. 그래서 {@link IngestReport}로
 * 유스케이스가 직접 세어 넘긴다. 이 값이 있어야 "매칭이 대부분 REJECTED다"(카탈로그 협소) 같은 신호가 보인다.
 *
 * <p>{@code followUpPriceChangedSent}·{@code followUpEndedSent}·{@code followUpVerifiedSent}는 이번 틱
 * <b>후속 알림</b> 발송 수다(AL-03, OBS-02 "알림 발송 수", Q-57·Q-13). 첫 알림과 부류가 달라 합치지 않고,
 * 후속끼리도 <b>PRICE_CHANGED·ENDED·VERIFIED를 가른다</b> — ENDED가 몰리면 딜이 대거 종료된 것이고
 * PRICE_CHANGED가 몰리면 가격이 움직인 것, VERIFIED가 몰리면 병합(교차검증)이 몰린 것이라 뜻이 다르다.
 * VERIFIED는 ingest의 병합 분기가 <b>첫 알림 재발송 대신</b> 흘려보내는 대상이다(Q-13 — 병합은 priceFirst가
 * 안 바뀌어 같은 트리거를 재평가하면 매번 다시 발화할 위험이 있었다). 발송 수는 스냅샷에 흔적이 없어(전송은
 * 상태 변화가 아니다) 스케줄러가 세어 넘긴다 — 안 그러면 {@code sendFollowUps}가 낸 값이 조용히 버려진다
 * ("첫 알림은 세는데 후속은 안 세는" 절반 카운터).
 *
 * <p>{@code reportCardsIssued}는 이번 틱에 발급한 성적표 수다(PUR-04). 발급은 REPORT_PENDING을 CLOSED로
 * 드레인하므로, 그냥 두면 {@code purchasesExpired}(= REPORT_PENDING 증가분)가 <b>"만료 − 발급"으로 오염</b>돼
 * 음수가 될 수 있다("카운터는 오염되지 않는 쪽을 센다"). 그래서 발급 수를 스케줄러가 직접 세어 넘기고,
 * {@code purchasesExpired}를 {@code Δ(REPORT_PENDING) + reportCardsIssued}로 <b>재구성</b>한다 — 두 카운터가
 * 서로 독립으로 정직해진다(발급 0이면 예전과 같다). 발급은 quiet(관통 알림 없음)라 텔레그램 토큰과 무관하다.
 *
 * <p>{@code stepsFailed}는 이번 틱에 예외를 던진 단계 수다(OBS-02, Q-56). {@code runStep}이 한 단계의 실패를
 * 격리하지만(다른 단계·다음 주기를 살린다), 격리는 <b>침묵</b>이기도 하다 — DB 스키마 불일치·락 충돌 같은
 * 지속 실패가 나면 파이프라인은 <b>도는 척하며 아무것도 처리하지 않는다.</b> 이 값이 매 틱 0이 아니면 그
 * 사실이 틱 로그 한 줄에 보인다(따로 `log.error`를 grep하지 않아도). 0을 생략하지 않는다 — 건강한 틱은
 * {@code stepsFailed=0}이라 비-0이 대비로 드러난다. 관리 알림(OBS-03)은 텔레그램 대기(Q-20)라 아직 카운터뿐이다.
 */
public record PipelineTickReport(
		long postsLinked,
		long dealsCreated,
		long merged,
		long queued,
		long ended,
		long purchasesExpired,
		long reportCardsIssued,
		long conditionsTagged,
		long conditionalTotal,
		long shippingUnknownTotal,
		long pending,
		long rawTotal,
		IngestReport ingest,
		int followUpPriceChangedSent,
		int followUpEndedSent,
		int followUpVerifiedSent,
		int stepsFailed,
		int heldAlertsFlushed,
		int heldAlertsDropped,
		FoldReport usedFold) {

	public static PipelineTickReport between(PipelineSnapshot before, PipelineSnapshot after, IngestReport ingest,
			int reportCardsIssued, int followUpPriceChangedSent, int followUpEndedSent, int followUpVerifiedSent,
			int stepsFailed, int heldAlertsFlushed, int heldAlertsDropped, FoldReport usedFold) {
		long postsLinked = after.linkedSources() - before.linkedSources();
		long dealsCreated = after.dealEvents() - before.dealEvents();
		// 발급이 REPORT_PENDING을 드레인하므로 Δ만으로는 만료 수가 아니다 — 발급 수를 더해 재구성한다.
		long purchasesExpired = (after.reportPendingPurchases() - before.reportPendingPurchases()) + reportCardsIssued;
		return new PipelineTickReport(
				postsLinked,
				dealsCreated,
				postsLinked - dealsCreated,
				after.reviewQueue() - before.reviewQueue(),
				after.endedDeals() - before.endedDeals(),
				purchasesExpired,
				reportCardsIssued,
				after.conditionalDeals() - before.conditionalDeals(),
				after.conditionalDeals(),
				after.shippingUnknownDeals(),
				after.unprocessed(),
				after.rawPosts(),
				ingest,
				followUpPriceChangedSent,
				followUpEndedSent,
				followUpVerifiedSent,
				stepsFailed,
				heldAlertsFlushed,
				heldAlertsDropped,
				usedFold);
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
				+ " reportCardsIssued=" + reportCardsIssued
				+ " conditionsTagged=" + conditionsTagged
				+ " conditionalTotal=" + conditionalTotal
				+ " shippingUnknownTotal=" + shippingUnknownTotal
				+ " pending=" + pending
				+ " rawTotal=" + rawTotal
				+ " matched[confirmed=" + ingest.confirmed()
				+ " candidate=" + ingest.candidate()
				+ " unknown=" + ingest.unknown()
				+ " rejected=" + ingest.rejected()
				+ " skippedNoPrice=" + ingest.skippedNoPrice()
				+ " skippedForeignSource=" + ingest.skippedForeignSource() + "]"
				+ " firstAlertsSent=" + ingest.firstAlertsSent()
				+ " heldAlerts=" + ingest.heldAlerts()
				+ " followUpsSent[priceChanged=" + followUpPriceChangedSent
				+ " ended=" + followUpEndedSent
				+ " verified=" + followUpVerifiedSent + "]"
				+ " heldFlushed[sent=" + heldAlertsFlushed + " dropped=" + heldAlertsDropped + "]"
				+ " usedBatchesFolded=" + usedFold.batches()
				// 관측한 사건과 **알린** 수를 따로 낸다 — 둘의 차이가 곧 "3계층 필터·목표가가 얼마나
				// 걸렀는가"다. 합치면 "알림이 안 나갔다"와 "매물이 없었다"를 구별할 수 없다.
				+ " usedLifecycle[appeared=" + usedFold.appeared()
				+ " revived=" + usedFold.revived()
				+ " priceChanged=" + usedFold.priceChanged()
				+ " disappeared=" + usedFold.disappeared() + "]"
				+ " usedAlerts[new=" + usedFold.alertsNew()
				+ " priceDrop=" + usedFold.alertsPriceDrop()
				+ " soldOut=" + usedFold.alertsSoldOut() + "]"
				+ " stepsFailed=" + stepsFailed;
	}
}
