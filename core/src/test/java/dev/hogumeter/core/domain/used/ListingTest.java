package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** USED-02 Listing 생애주기 상태기계 + AC-11 상세 fetch 승격 시 1회. */
class ListingTest {

	private static Listing active() {
		return Listing.observed("A", "아이폰17 256 S급", 900_000);
	}

	@Test
	void observedStartsActiveUnpromotedUnfetched() {
		Listing l = active();
		assertThat(l.status()).isEqualTo(ListingStatus.ACTIVE);
		assertThat(l.promoted()).isFalse();
		assertThat(l.detailFetched()).isFalse();
	}

	@Test
	void activeCanBecomeSoldOrRemoved() {
		assertThat(active().markSold().status()).isEqualTo(ListingStatus.SOLD);
		assertThat(active().markRemoved().status()).isEqualTo(ListingStatus.REMOVED);
	}

	@Test
	void terminalStatusRejectsFurtherTransition() {
		assertThatThrownBy(() -> active().markSold().markRemoved()).isInstanceOf(IllegalStateException.class);
	}

	// AC-11: 승격 시 아직 안 받은 매물만 fetch 대상, 받은 뒤엔 아님, 미승격은 아님
	@Test
	void needsDetailFetchOnlyWhenPromotedAndNotYetFetched() {
		assertThat(active().needsDetailFetch()).isFalse(); // 미승격
		assertThat(active().promote().needsDetailFetch()).isTrue(); // 승격·미fetch
		assertThat(active().promote().withDetailFetched().needsDetailFetch()).isFalse(); // fetch 완료
	}
}
