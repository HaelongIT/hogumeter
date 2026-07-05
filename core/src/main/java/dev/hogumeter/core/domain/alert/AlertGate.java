package dev.hogumeter.core.domain.alert;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * AL-04 발송 게이트(순수 도메인, Clock 주입). 🔥(JACKPOT)는 방해금지를 관통해 즉시 발송,
 * 그 외는 방해금지 시간이면 보류(HOLD). 보류분의 종료 시 플러시는 스케줄러/어댑터가 이 판정을 재사용한다.
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
