package dev.hogumeter.core.domain.used;

import java.util.List;

/**
 * 중고 3계층 조건검색 스펙(USED-01, docs/used/02·04). 순수 매칭 입력 — 목표가·폴링주기 같은 알림/수집
 * 파라미터는 여기 없다(각각 알림 판정·스케줄러의 몫).
 *
 * <ul>
 *   <li>{@code required} — AND. 전부 매칭돼야 후보(제품 정체성).</li>
 *   <li>{@code bonusGroups} — OR 그룹. 각 그룹 모드(SORT|TRIGGER)에 따라 배지/알림조건.</li>
 *   <li>{@code exclude} — NOT. 하나라도 히트하면 즉시 탈락(required보다 우선).</li>
 * </ul>
 */
public record UsedSearchSpec(List<String> required, List<BonusGroup> bonusGroups, List<String> exclude) {

	public UsedSearchSpec {
		required = List.copyOf(required);
		bonusGroups = List.copyOf(bonusGroups);
		exclude = List.copyOf(exclude);
	}
}
