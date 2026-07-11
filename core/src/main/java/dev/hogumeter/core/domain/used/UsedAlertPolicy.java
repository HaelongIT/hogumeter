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

	/**
	 * AC-8 후속 알림: 승격된(promoted) 매물의 <b>가격 하락</b>만 후속 알림한다(더 좋은 딜). 상승은 배지·정렬만
	 * (스팸 방지). 미승격 매물의 변동은 알림 안 함. 하락만 두는 것은 잠정(docs/91 Q-70) — seam은 이 한 줄.
	 */
	public static boolean shouldAlertOnPriceChange(PriceChange change, boolean promoted) {
		return promoted && change.currentPrice() < change.previousPrice();
	}

	/** AC-9 후속 알림: 승격된 매물이 목록에서 소실(판매완료 추정)되면 알림. 미승격은 스냅샷 전체 미적용. */
	public static boolean shouldAlertOnSoldOut(boolean promoted) {
		return promoted;
	}
}
