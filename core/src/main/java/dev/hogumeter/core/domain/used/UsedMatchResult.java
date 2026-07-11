package dev.hogumeter.core.domain.used;

/**
 * 3계층 필터 판정 결과(USED-01). 알림 발화 조건은 {@link #alertEligible()} = 후보 AND 트리거 충족.
 * SORT 배지는 알림에 기여하지 않고 표시(정렬·배지)에만 쓴다(docs/used/04 AC-2).
 *
 * @param candidate required 전부 매칭 AND exclude 미히트
 * @param triggerSatisfied 모든 TRIGGER 그룹이 각각 그룹내 OR로 충족(TRIGGER 그룹 없으면 true)
 * @param hasSortBadge SORT 그룹 중 하나라도 매칭(표시용)
 */
public record UsedMatchResult(boolean candidate, boolean triggerSatisfied, boolean hasSortBadge) {

	public boolean alertEligible() {
		return candidate && triggerSatisfied;
	}
}
