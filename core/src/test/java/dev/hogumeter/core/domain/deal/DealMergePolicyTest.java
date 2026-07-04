package dev.hogumeter.core.domain.deal;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.BenchmarkParams;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BM-04 딜 병합·교차검증. 병합 조건 = 동일 대상(variant 또는 미상 후보 겹침) + 가격 차 ≤ 허용폭(기준=기존 딜가)
 * + 시간 차 ≤ 윈도. 경계값 정확 포함/제외 테스트 필수(AC-2).
 */
class DealMergePolicyTest {

	private final DealMergePolicy policy = new DealMergePolicy();
	private final BenchmarkParams params = BenchmarkParams.defaults(); // ratio 2%, floor 5,000, window 48h

	private static DealEvent deal(long variant, long price, String site, String firstSeenIso, DealStatus status) {
		return aDealEvent().withVariantId(variant).withPriceFirst(price).withSite(site)
				.withSourceSites(site).firstSeen(firstSeenIso).status(status).build();
	}

	// ---- AC-1 병합 → 교차검증 → VERIFIED ----
	@Test
	void mergesTwoDifferentSitesIntoVerifiedDeal() {
		DealEvent existing = deal(1, 1_000_000, "ppomppu", "2026-06-01T00:00:00Z", DealStatus.ACTIVE);
		DealEvent incoming = deal(1, 1_010_000, "ruliweb", "2026-06-01T06:00:00Z", DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, incoming, params)).isTrue();
		DealEvent merged = policy.merge(existing, incoming);

		assertThat(merged.sourceSites()).containsExactlyInAnyOrder("ppomppu", "ruliweb");
		assertThat(merged.crossVerified()).isTrue();
		assertThat(merged.status()).isEqualTo(DealStatus.VERIFIED);
		assertThat(merged.priceFirst()).isEqualTo(1_000_000L); // 먼저 관측된 글의 가격
		assertThat(merged.priceMin()).isEqualTo(1_000_000L);
		assertThat(merged.priceMax()).isEqualTo(1_010_000L);
	}

	// ---- AC-2 가격 경계(기준=기존가 1,000,000, 허용 ±20,000) ----
	@ParameterizedTest(name = "incoming={0} → merge={1}")
	@CsvSource({ "1020000, true", "1020001, false", "980000, true", "979999, false" })
	void priceToleranceBoundary(long incomingPrice, boolean expected) {
		DealEvent existing = deal(1, 1_000_000, "ppomppu", "2026-06-01T00:00:00Z", DealStatus.ACTIVE);
		DealEvent incoming = deal(1, incomingPrice, "ruliweb", "2026-06-01T06:00:00Z", DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, incoming, params)).isEqualTo(expected);
	}

	// ---- AC-2 절대 하한(저가품: 기존 100,000 → 비율 2,000 < 하한 5,000 → 허용 5,000) ----
	@ParameterizedTest(name = "incoming={0} → merge={1}")
	@CsvSource({ "105000, true", "105001, false" })
	void priceFloorBoundaryForCheapItems(long incomingPrice, boolean expected) {
		DealEvent existing = deal(1, 100_000, "ppomppu", "2026-06-01T00:00:00Z", DealStatus.ACTIVE);
		DealEvent incoming = deal(1, incomingPrice, "ruliweb", "2026-06-01T06:00:00Z", DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, incoming, params)).isEqualTo(expected);
	}

	// ---- AC-2 시간 윈도 경계(48h 정확 포함, +1s 제외) ----
	@ParameterizedTest(name = "incomingTime={0} → merge={1}")
	@CsvSource({ "2026-06-03T00:00:00Z, true", "2026-06-03T00:00:01Z, false" })
	void timeWindowBoundary(String incomingFirstSeen, boolean expected) {
		DealEvent existing = deal(1, 1_000_000, "ppomppu", "2026-06-01T00:00:00Z", DealStatus.ACTIVE);
		DealEvent incoming = deal(1, 1_000_000, "ruliweb", incomingFirstSeen, DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, incoming, params)).isEqualTo(expected);
	}

	// ---- AC-3 미상끼리 잠정 병합(후보 겹침) ----
	@Test
	void unclassifiedDealsTentativelyMergeOnCandidateOverlap() {
		DealEvent a = aDealEvent().unclassified(Set.of(1L, 2L)).withPriceFirst(500_000).withSite("ppomppu")
				.withSourceSites("ppomppu").firstSeen("2026-06-01T00:00:00Z").status(DealStatus.ACTIVE).build();
		DealEvent b = aDealEvent().unclassified(Set.of(2L, 3L)).withPriceFirst(505_000).withSite("ruliweb")
				.withSourceSites("ruliweb").firstSeen("2026-06-01T06:00:00Z").status(DealStatus.ACTIVE).build();

		assertThat(policy.canMerge(a, b, params)).isTrue();
		DealEvent merged = policy.merge(a, b);

		assertThat(merged.unclassified()).isTrue();
		assertThat(merged.productCandidates()).containsExactlyInAnyOrder(1L, 2L, 3L);
		assertThat(merged.sourceSites()).hasSize(2);
	}

	@Test
	void unclassifiedDealsWithDisjointCandidatesDoNotMerge() {
		DealEvent a = aDealEvent().unclassified(Set.of(1L, 2L)).withPriceFirst(500_000).withSite("ppomppu")
				.withSourceSites("ppomppu").firstSeen("2026-06-01T00:00:00Z").status(DealStatus.ACTIVE).build();
		DealEvent b = aDealEvent().unclassified(Set.of(3L, 4L)).withPriceFirst(505_000).withSite("ruliweb")
				.withSourceSites("ruliweb").firstSeen("2026-06-01T06:00:00Z").status(DealStatus.ACTIVE).build();

		assertThat(policy.canMerge(a, b, params)).isFalse();
	}

	// ---- AC-4 흡수된 3번째 사이트는 새 첫 알림을 만들지 않는다(VERIFIED 유지, NEW 리셋 없음) ----
	@Test
	void absorbingThirdSiteKeepsVerifiedWithoutResettingToNew() {
		DealEvent existing = aDealEvent().withVariantId(1L).withPriceFirst(1_000_000)
				.withSourceSites("ppomppu", "ruliweb").status(DealStatus.VERIFIED)
				.firstSeen("2026-06-01T00:00:00Z").build();
		DealEvent third = deal(1, 1_005_000, "fmkorea", "2026-06-01T12:00:00Z", DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, third, params)).isTrue();
		DealEvent merged = policy.merge(existing, third);

		assertThat(merged.sourceSites()).hasSize(3);
		assertThat(merged.status()).isEqualTo(DealStatus.VERIFIED); // 새 첫 알림 유발하는 NEW 리셋 없음
	}

	// ---- 다른 variant는 병합 안 됨 ----
	@Test
	void differentVariantDoesNotMerge() {
		DealEvent existing = deal(1, 1_000_000, "ppomppu", "2026-06-01T00:00:00Z", DealStatus.ACTIVE);
		DealEvent incoming = deal(2, 1_000_000, "ruliweb", "2026-06-01T06:00:00Z", DealStatus.ACTIVE);

		assertThat(policy.canMerge(existing, incoming, params)).isFalse();
	}
}
