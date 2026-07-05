package dev.hogumeter.core.domain.alert;

import java.util.List;

/**
 * 알림 판정 결과(순수 값) — "무엇을 보내기로 했는가". 발송 여부·최고 강도·병기할 나머지 조건·딱지 라벨.
 *
 * @param intensity 최고 강도(발송 안 하면 NONE)
 * @param alsoSatisfied 최고 외 충족 조건(본문 병기용, 우선순위 순)
 * @param labels 딱지(미검증/N개 사이트 검증, 표본 N건 참고용, 기준 미확립 참고용 등)
 */
public record AlertDecision(boolean shouldAlert, AlertIntensity intensity, List<AlertIntensity> alsoSatisfied,
		List<String> labels) {

	public AlertDecision {
		alsoSatisfied = List.copyOf(alsoSatisfied);
		labels = List.copyOf(labels);
	}
}
