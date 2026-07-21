package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.application.port.out.AdminNotifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * OBS-03 연속 실패 알림의 순수 계약: 임계에 처음 닿는 순간만 알린다(스팸 방지), 건강해지면 리셋,
 * 리셋 뒤 다시 임계를 넘으면 또 알린다. 임계 미만의 산발적 실패엔 침묵한다.
 */
class PipelineHealthMonitorTest {

	private final List<String> notified = new ArrayList<>();
	private final AdminNotifier recorder = notified::add;

	private PipelineHealthMonitor monitor(int threshold) {
		return new PipelineHealthMonitor(recorder, threshold);
	}

	@Test
	void alertsOnceWhenFailuresReachThreshold() {
		PipelineHealthMonitor monitor = monitor(3);

		monitor.onTick(false);
		monitor.onTick(false);
		assertThat(notified).as("임계 전에는 침묵").isEmpty();
		monitor.onTick(false);
		assertThat(notified).as("3연속에 딱 한 번").hasSize(1);
	}

	@Test
	void doesNotReAlertWhileStillFailingPastThreshold() {
		PipelineHealthMonitor monitor = monitor(3);

		for (int i = 0; i < 6; i++) {
			monitor.onTick(false);
		}

		assertThat(notified).as("계속 실패해도 재알림 안 함(스팸 방지)").hasSize(1);
	}

	@Test
	void healthyTickResetsTheStreak() {
		PipelineHealthMonitor monitor = monitor(3);

		monitor.onTick(false);
		monitor.onTick(false);
		monitor.onTick(true); // 회복 — 연속 끊김
		monitor.onTick(false);
		monitor.onTick(false);

		assertThat(notified).as("리셋됐으니 아직 임계 미만").isEmpty();
	}

	@Test
	void alertsAgainAfterRecoveryAndReFailure() {
		PipelineHealthMonitor monitor = monitor(2);

		monitor.onTick(false);
		monitor.onTick(false); // 1차 알림
		monitor.onTick(true); // 회복
		monitor.onTick(false);
		monitor.onTick(false); // 2차 알림(새 에피소드)

		assertThat(notified).hasSize(2);
	}
}
