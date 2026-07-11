package dev.hogumeter.core.domain.used;

import java.util.List;

/**
 * 연속 두 스냅샷 diff 결과(USED-02, docs/used/04 AC-7~10). <b>사실만</b> 낸다 — 신규·가격변동·소실.
 * "promoted 매물만 후속 알림"(AC-8·9) 같은 알림 정책 필터는 이 결과를 소비하는 알림 판정 층의 몫이지
 * diff의 몫이 아니다(diff는 순수·무정책).
 *
 * @param appeared 이전에 없던 매물(신규, AC-7). 끌올(양쪽 존재)은 여기 포함되지 않음(AC-10)
 * @param priceChanged 양쪽에 있고 가격이 달라진 매물(AC-8)
 * @param disappeared 이전에 있었으나 이번 목록에서 빠진 listingId(판매완료 추정, AC-9)
 */
public record ListingDiffResult(List<ObservedListing> appeared, List<PriceChange> priceChanged,
		List<String> disappeared) {

	public ListingDiffResult {
		appeared = List.copyOf(appeared);
		priceChanged = List.copyOf(priceChanged);
		disappeared = List.copyOf(disappeared);
	}
}
