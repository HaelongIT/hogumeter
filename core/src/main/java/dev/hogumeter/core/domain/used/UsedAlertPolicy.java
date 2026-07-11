package dev.hogumeter.core.domain.used;

/**
 * USED-02 알림 발화 판정(순수, IO 0). 신규 매물이 3계층 필터를 통과(required AND + TRIGGER 충족)하고
 * 목표가 이하이면 알림한다(docs/used/04 AC-7). SORT 매칭은 배지·정렬만이라 여기 관여하지 않는다.
 *
 * <p>가격변동·판매완료 후속 알림(AC-8·9)은 promoted 매물 한정이라 Listing 상태를 받아 별도 판정한다.
 */
public final class UsedAlertPolicy {

	private UsedAlertPolicy() {
	}

	/**
	 * @param targetPrice 목표가(선택). null이면 가격 조건 없이 필터 통과만으로 알림(놓침보다 관대).
	 */
	public static boolean shouldAlertOnNew(String title, long price, UsedSearchSpec spec, Long targetPrice) {
		return UsedMatcher.evaluate(title, spec).alertEligible()
				&& (targetPrice == null || price <= targetPrice);
	}
}
