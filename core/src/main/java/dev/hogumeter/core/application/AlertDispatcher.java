package dev.hogumeter.core.application;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.alert.AlertDecision;
import dev.hogumeter.core.domain.alert.AlertEvaluator;
import dev.hogumeter.core.domain.alert.AlertGate;
import dev.hogumeter.core.domain.alert.AlertPolicy;
import dev.hogumeter.core.domain.alert.GateDecision;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.deal.DealEvent;
import java.time.Clock;
import java.util.function.LongFunction;

/**
 * 신규 딜 → 알림 판정 → 게이트 → 발송을 잇는 유스케이스. 순수 도메인(평가·게이트)을 조립하고
 * 아웃 포트(AlertSender)로만 발송한다. HOLD(방해금지)는 {@code EvaluateAlertOnDeal}이 {@code held_alert}
 * 큐에 적고, {@code FlushHeldAlertsUseCase}가 방해금지 종료 후 재평가해 보낸다(Q-20 ②).
 */
public class AlertDispatcher {

	private final AlertEvaluator evaluator;
	private final AlertGate gate;
	private final AlertSender sender;
	private final LongFunction<VariantNaming.Naming> naming; // AL-05 제품/variant 이름(발송 시에만 조회)

	public AlertDispatcher(AlertEvaluator evaluator, AlertGate gate, AlertSender sender,
			LongFunction<VariantNaming.Naming> naming) {
		this.evaluator = evaluator;
		this.gate = gate;
		this.sender = sender;
		this.naming = naming;
	}

	public DispatchOutcome dispatch(DealEvent deal, BenchmarkView view, AlertPolicy policy,
			BenchmarkParams params, Clock clock, long dealEventId) {
		return dispatch(deal, view, policy, params, clock, false, dealEventId);
	}

	/** PUR-03 paidPrice 하회 트리거(활성 관찰)를 함께 반영해 판정한다. dealEventId는 [무시] 버튼(Q-22)에 실린다. */
	public DispatchOutcome dispatch(DealEvent deal, BenchmarkView view, AlertPolicy policy,
			BenchmarkParams params, Clock clock, boolean paidPriceTriggerFires, long dealEventId) {
		AlertDecision decision = evaluator.evaluate(deal, view, policy, params, paidPriceTriggerFires);
		if (!decision.shouldAlert()) {
			return DispatchOutcome.NO_ALERT;
		}
		if (gate.decide(decision, policy, clock) == GateDecision.SEND_NOW) {
			VariantNaming.Naming n = (deal.variantId() == null)
					? VariantNaming.Naming.UNKNOWN : naming.apply(deal.variantId());
			sender.send(new AlertMessage(deal, view, decision, null, n.productName(), n.variantLabel(), dealEventId));
			return DispatchOutcome.SENT;
		}
		return DispatchOutcome.HELD;
	}
}
