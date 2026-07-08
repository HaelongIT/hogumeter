package dev.hogumeter.core.domain.signal;

import java.util.List;

/**
 * SIG 신호등 결과(docs/16). 표시 전용 — 알림 트리거 아님(B-5 비대칭: 상시 표시는 signalSet만 단정).
 *
 * @param goodDealLineEstablished m&gt;0 여부(false면 🟢 불가 + "굿딜라인 미확립")
 * @param notes 딱지("굿딜라인 미확립", "신선도 약화" 등)
 */
public record SignalView(SignalColor color, boolean goodDealLineEstablished, List<String> notes) {

	public SignalView {
		notes = List.copyOf(notes);
	}
}
