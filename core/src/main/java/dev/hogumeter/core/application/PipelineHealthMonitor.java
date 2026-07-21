package dev.hogumeter.core.application;

import dev.hogumeter.core.application.port.out.AdminNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OBS-03(Q-56 잔여): 파이프라인이 <b>연속으로</b> 실패하면 운영자에게 알린다. 스키마 불일치·락 충돌 같은
 * 지속 실패는 "도는 척하며 아무것도 안 하는" 상태다 — {@code stepsFailed} 카운터로 <b>보이게</b는 했지만,
 * 로그를 보지 않는 1인용 사용자에겐 그것만으론 부족하다. 연속 실패 임계를 넘으면 push한다.
 *
 * <p><b>임계에 처음 닿은 순간만</b> 알린다(스팸 방지) — 계속 실패해도 재알림하지 않고, 한 번 건강해져
 * 리셋된 뒤 다시 임계를 넘으면 그때 또 알린다. 임계 기본 3은 collector의 {@code SINK_FAILURE_LIMIT}과 정합.
 * 일시적 한두 틱 실패(재폴링으로 회복)에 알리지 않으려는 값이다.
 */
@Component
public class PipelineHealthMonitor {

	private final AdminNotifier notifier;
	private final int threshold;
	private int failStreak;

	public PipelineHealthMonitor(AdminNotifier notifier,
			@Value("${pipeline.failure-alert-threshold:3}") int threshold) {
		this.notifier = notifier;
		this.threshold = threshold;
	}

	/**
	 * 한 틱의 건강 여부를 받는다. 불건강이 임계만큼 연속되면 딱 한 번 알린다. 건강하면 연속을 끊는다.
	 * {@code @Scheduled(fixedDelay)}는 틱이 겹치지 않아 상태 하나로 안전하다(스케줄러의 {@code stepFailures}와 같은 이유).
	 */
	public void onTick(boolean healthy) {
		if (healthy) {
			failStreak = 0;
			return;
		}
		failStreak++;
		if (failStreak == threshold) {
			notifier.notify("파이프라인이 " + threshold + "틱 연속 실패했습니다. `docker logs`로 원인을 확인하세요.");
		}
	}
}
