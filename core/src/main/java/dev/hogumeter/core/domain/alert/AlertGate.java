package dev.hogumeter.core.domain.alert;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * AL-04 발송 게이트(순수 도메인, Clock 주입). 🔥(JACKPOT)는 방해금지를 관통해 즉시 발송,
 * 그 외는 방해금지 시간이면 보류(HOLD). 보류분은 {@code held_alert} 큐에 적히고, 방해금지가 끝난 틱에
 * {@code FlushHeldAlertsUseCase}가 <b>재평가해</b> 보낸다(Q-20 ②) — 저장된 본문이 아니라 발송 시점 상태로.
 */
public class AlertGate {

	public GateDecision decide(AlertDecision decision, AlertPolicy policy, Clock clock) {
		if (decision.intensity() == AlertIntensity.JACKPOT) {
			return GateDecision.SEND_NOW; // 🔥 관통
		}
		int hour = ZonedDateTime.now(clock).getHour();
		if (QuietHours.isQuiet(hour, policy.quietHoursStart(), policy.quietHoursEnd())) {
			return GateDecision.HOLD;
		}
		return GateDecision.SEND_NOW;
	}
}
