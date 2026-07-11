package dev.hogumeter.core.domain.used;

import java.util.ArrayList;
import java.util.List;

/**
 * USED-04 위험 신호 수집(순수, IO 0). 매물 텍스트에서 업자 레퍼토리 키워드 히트와 "스냅샷 최저 대비 과도하게
 * 저렴" 플래그를 <b>나열</b>한다. 판정하지 않는다(AC-14) — 결론은 사람이 낸다.
 */
public final class UsedRiskSignals {

	public static final String CATEGORY_REPERTOIRE = "업자 레퍼토리 키워드";
	public static final String CATEGORY_PRICE = "가격 이상";

	private UsedRiskSignals() {
	}

	/**
	 * @param snapshotLowest 활성 매물 스냅샷 최저가(없으면 null — 플래그 생략)
	 * @param cheapThresholdPct 최저 대비 이 % 이상 저렴하면 가격 플래그(예: 30 = 30% 이상 저렴)
	 */
	public static List<RiskSignal> detect(String text, List<String> repertoireKeywords, long price,
			Long snapshotLowest, int cheapThresholdPct) {
		List<RiskSignal> signals = new ArrayList<>();
		String norm = UsedMatcher.normalize(text);
		for (String keyword : repertoireKeywords) {
			if (norm.contains(UsedMatcher.normalize(keyword))) {
				signals.add(new RiskSignal(CATEGORY_REPERTOIRE, keyword)); // 나열 — 원문 표현 그대로
			}
		}
		if (snapshotLowest != null && snapshotLowest > 0) {
			long threshold = snapshotLowest * (100 - cheapThresholdPct) / 100;
			if (price <= threshold) {
				signals.add(new RiskSignal(CATEGORY_PRICE, "스냅샷 최저 대비 " + cheapThresholdPct + "% 이상 저렴"));
			}
		}
		return List.copyOf(signals);
	}
}
