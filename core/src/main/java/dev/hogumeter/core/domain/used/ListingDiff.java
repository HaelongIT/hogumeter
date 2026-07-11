package dev.hogumeter.core.domain.used;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * USED-02 목록 diff(순수, IO 0). 연속된 두 목록 스냅샷을 비교해 신규·가격변동·소실을 낸다. 매물 개별
 * 재방문이 아니라 검색 목록 페이지 한 번으로 전 생애주기를 감지한다(부하 O(검색 수)).
 *
 * <p>같은 스냅샷 안의 중복 listingId는 스냅샷 단위로 dedupe한다(마지막 관측 승리). 결정성을 위해 입력
 * 순서를 보존한다 — 정렬이 환경(로케일)을 타지 않도록 자연키 기준 안정 순서만 사용.
 */
public final class ListingDiff {

	private ListingDiff() {
	}

	public static ListingDiffResult diff(Collection<ObservedListing> previous, Collection<ObservedListing> current) {
		Map<String, ObservedListing> prev = index(previous);
		Map<String, ObservedListing> curr = index(current);

		List<ObservedListing> appeared = curr.values().stream()
				.filter(l -> !prev.containsKey(l.listingId()))
				.toList();
		List<PriceChange> priceChanged = curr.values().stream()
				.filter(l -> prev.containsKey(l.listingId()))
				.filter(l -> prev.get(l.listingId()).price() != l.price())
				.map(l -> new PriceChange(l.listingId(), prev.get(l.listingId()).price(), l.price()))
				.toList();
		List<String> disappeared = prev.keySet().stream()
				.filter(id -> !curr.containsKey(id))
				.toList();

		return new ListingDiffResult(appeared, priceChanged, disappeared);
	}

	/** 스냅샷 단위 dedupe(같은 listingId는 마지막 관측 승리) + 입력 순서 보존(결정성). */
	private static Map<String, ObservedListing> index(Collection<ObservedListing> listings) {
		Map<String, ObservedListing> byId = new LinkedHashMap<>();
		for (ObservedListing l : listings) {
			byId.put(l.listingId(), l);
		}
		return byId;
	}
}
