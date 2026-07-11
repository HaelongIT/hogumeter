package dev.hogumeter.core.domain.used;

import java.util.List;

/**
 * 보너스 키워드 한 그룹 = 동의어 나열 + 모드(USED-01, docs/used/04 AC-5). 그룹 <b>안</b>은 OR
 * (하나라도 매칭되면 그룹 히트). 내장 동의어 사전은 없다 — 사용자가 그룹에 나열했기 때문에만 같다.
 */
public record BonusGroup(List<String> keywords, BonusMode mode) {

	public BonusGroup {
		keywords = List.copyOf(keywords);
	}
}
