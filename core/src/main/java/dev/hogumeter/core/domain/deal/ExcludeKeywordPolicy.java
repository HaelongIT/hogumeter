package dev.hogumeter.core.domain.deal;

import java.util.Set;

/**
 * BM-05 AC-4 제외 키워드 정책(순수 도메인). 제목이 제외 키워드에 걸리면
 * 정책이 EXCLUDE(기본)면 배제, LABEL이면 ⚠️의심 라벨만. 가격·이상치 판정과 독립.
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

	public Verdict evaluate(String title, Set<String> excludeKeywords, Mode mode) {
		boolean hit = excludeKeywords.stream().anyMatch(title::contains);
		if (!hit) {
			return Verdict.CLEAN;
		}
		return mode == Mode.EXCLUDE ? Verdict.EXCLUDED : Verdict.LABELED;
	}
}
