package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** USED-02 목록 diff 인수조건(docs/used/04 AC-7~10) — 순수, IO 0. */
class ListingDiffTest {

	private static ObservedListing l(String id, long price) {
		return new ObservedListing(id, price);
	}

	// AC-7. 신규 매물 감지 — 이전에 없던 listingId
	@Test
	void detectsNewListing() {
		ListingDiffResult r = ListingDiff.diff(List.of(l("A", 900_000)), List.of(l("A", 900_000), l("B", 800_000)));

		assertThat(r.appeared()).containsExactly(l("B", 800_000));
		assertThat(r.priceChanged()).isEmpty();
		assertThat(r.disappeared()).isEmpty();
	}

	// AC-8. 가격변동 감지 — 같은 listingId, 다른 가격
	@Test
	void detectsPriceChange() {
		ListingDiffResult r = ListingDiff.diff(List.of(l("A", 900_000)), List.of(l("A", 850_000)));

		assertThat(r.priceChanged()).containsExactly(new PriceChange("A", 900_000, 850_000));
		assertThat(r.appeared()).isEmpty();
		assertThat(r.disappeared()).isEmpty();
	}

	// AC-9. 판매완료(소실) 감지 — 이전에 있고 이번에 없음
	@Test
	void detectsDisappearance() {
		ListingDiffResult r = ListingDiff.diff(List.of(l("A", 900_000), l("B", 800_000)), List.of(l("B", 800_000)));

		assertThat(r.disappeared()).containsExactly("A");
		assertThat(r.appeared()).isEmpty();
		assertThat(r.priceChanged()).isEmpty();
	}

	// AC-10. 끌올 dedupe — 같은 listingId 재등장은 신규도 변동도 아님
	@Test
	void bumpedListingIsNeitherNewNorChanged() {
		ListingDiffResult r = ListingDiff.diff(List.of(l("A", 900_000)), List.of(l("A", 900_000)));

		assertThat(r.appeared()).isEmpty();
		assertThat(r.priceChanged()).isEmpty();
		assertThat(r.disappeared()).isEmpty();
	}

	// 스냅샷 내 같은 listingId 중복은 스냅샷 단위 dedupe(마지막 관측 승리)
	@Test
	void dedupesWithinSnapshot() {
		ListingDiffResult r = ListingDiff.diff(List.of(), List.of(l("A", 900_000), l("A", 880_000)));

		assertThat(r.appeared()).containsExactly(l("A", 880_000));
	}
}
