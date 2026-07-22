package dev.hogumeter.core.domain.deal;

import java.util.List;
import java.util.Set;

/**
 * BM-05 AC-4 제외 키워드 정책(순수 도메인). 제목이 제외 키워드에 걸리면 EXCLUDE(기본)·LABEL 모두
 * **전 통계(pricingSet/occurrenceSet/signalSet)에서 제외**된다(v1.3 C-5). 두 모드의 차이는 **표시 가시성뿐** —
 * EXCLUDE는 숨김, LABEL은 ⚠️의심 라벨로 노출하되 통계 산입은 안 한다. 가격·이상치 판정과 독립.
 * (Verdict는 표시 처리 구분용 — 표본 조립 소비처가 LABELED도 EXCLUDED와 동일하게 통계 제외해야 함, docs/91 후속.)
 */
public class ExcludeKeywordPolicy {

	public enum Mode {
		EXCLUDE,
		LABEL
	}

	public enum Verdict {
		EXCLUDED,
		LABELED,
		CLEAN
	}

	/**
	 * 제외 키워드 목록 정규화의 <b>정본</b> — 공백 다듬기·빈 값 탈락·중복 접기.
	 *
	 * <p>per-product 정책({@code AlertPolicySettings})과 <b>전역 설정</b>(Q-28 ①)이 반드시 같은 규칙을 써야 한다 —
	 * 두 곳이 각자 정규화하면 한쪽이 조용히 다른 목록을 만들고, 합쳐질 때 중복·공백 차이로 어긋난다.
	 */
	public static List<String> normalize(List<String> keywords) {
		return keywords == null ? List.of()
				: keywords.stream().map(String::trim).filter(k -> !k.isEmpty()).distinct().toList();
	}

	public Verdict evaluate(String title, Set<String> excludeKeywords, Mode mode) {
		boolean hit = excludeKeywords.stream().anyMatch(title::contains);
		if (!hit) {
			return Verdict.CLEAN;
		}
		return mode == Mode.EXCLUDE ? Verdict.EXCLUDED : Verdict.LABELED;
	}
}
