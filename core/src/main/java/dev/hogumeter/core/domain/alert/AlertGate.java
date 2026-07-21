package dev.hogumeter.core.domain.alert;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * AL-04 발송 게이트(순수 도메인, Clock 주입). 🔥(JACKPOT)는 방해금지를 관통해 즉시 발송,
 * 그 외는 방해금지 시간이면 보류(HOLD). ⚠️ 보류분의 <b>종료 시 플러시는 아직 없다</b>(Q-20 ②) — 즉 지금
 * HOLD 판정을 받은 알림은 유실된다. 그 손실은 {@code IngestReport.heldAlerts}로 카운트돼 눈에 보인다.
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
