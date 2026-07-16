package dev.hogumeter.core.domain.dealset;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.DealTags;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * docs/03 3-1 세 집합 — 동일 딜이 집합(값/시간/지금)마다 다르게 분류되는지.
 * LOWER 3상태는 (outlierFlag, permanentlyExcluded)로 도출: 미확정=LOWER&!perm / 승격=NONE / 기각=LOWER&perm.
 */
class DealSetsTest {

	private final DealEvent upper = aDealEvent().withPriceFirst(1).outlier(OutlierFlag.UPPER).build();
	private final DealEvent lowerPending = aDealEvent().withPriceFirst(2).outlier(OutlierFlag.LOWER).build();
	private final DealEvent lowerRejected =
			aDealEvent().withPriceFirst(3).outlier(OutlierFlag.LOWER).permanentlyExcluded().build();
	private final DealEvent normalActive = aDealEvent().withPriceFirst(4).status(DealStatus.ACTIVE).build();
	private final DealEvent normalEnded = aDealEvent().withPriceFirst(5).status(DealStatus.ENDED).build();
	private final DealEvent unclassified = aDealEvent().withPriceFirst(6).unclassified(Set.of(1L)).build();

	private final List<DealEvent> all =
			List.of(upper, lowerPending, lowerRejected, normalActive, normalEnded, unclassified);

	@Test
	void pricingSetKeepsNonOutlierClassifiedDeals() {
		// 값 통계: 이상치 미해당(승격 LOWER=NONE 포함) + 미상 제외
		assertThat(DealSets.pricingSet(all)).containsExactly(normalActive, normalEnded);
	}

	@Test
	void pricingSetExcludesShippingUnknown() {
		// 배송비미상 딜은 저장가가 하한이라 median/P25를 아래로 끈다 → 값 통계에서 제외(Q-46 ②).
		DealEvent shippingUnknown = aDealEvent().withPriceFirst(7).appliedConditions(DealTags.SHIPPING_UNKNOWN).build();
		List<DealEvent> deals = List.of(normalActive, shippingUnknown);

		assertThat(DealSets.pricingSet(deals)).containsExactly(normalActive);
		// 그래도 실제 딜이므로 발생·신호 집합엔 남는다(가격만 못 믿을 뿐).
		assertThat(DealSets.occurrenceSet(deals)).contains(shippingUnknown);
		assertThat(DealSets.signalSet(deals)).contains(shippingUnknown);
	}

	@Test
	void occurrenceSetKeepsIdentityDealsExcludingUpperAndRejectedLower() {
		// 시간 통계: UPPER 제외, LOWER는 기각만 제외(미확정 포함), ENDED 포함
		assertThat(DealSets.occurrenceSet(all)).containsExactly(lowerPending, normalActive, normalEnded);
	}

	@Test
	void signalSetKeepsLiveNonOutlierDeals() {
		// 지금 단정: 비이상치 + ENDED 아님 (신선도는 다운스트림)
		assertThat(DealSets.signalSet(all)).containsExactly(normalActive);
	}

	@Test
	void unclassifiedIsExcludedFromEverySet() {
		List<DealEvent> onlyUnclassified = List.of(unclassified);
		assertThat(DealSets.pricingSet(onlyUnclassified)).isEmpty();
		assertThat(DealSets.occurrenceSet(onlyUnclassified)).isEmpty();
		assertThat(DealSets.signalSet(onlyUnclassified)).isEmpty();
	}

	/**
	 * Q-66 ① 수요축 범위(확정본 §40·41). 묶음은 한 분포를 공유하고, 분리는 값별로 갈린다.
	 * 미상 딜은 분리에서 빠진다 — 그게 §41의 "값 미상 버킷은 기준가 계산 제외"다.
	 */
	@Nested
	class DemandScope {

		private final DealEvent black = aDealEvent().withPriceFirst(10).demandAxisValue("블랙").build();
		private final DealEvent white = aDealEvent().withPriceFirst(20).demandAxisValue("화이트").build();
		private final DealEvent unknownColor = aDealEvent().withPriceFirst(30).build(); // 값 미상
		private final List<DealEvent> all = List.of(black, white, unknownColor);

		@Test
		void groupedSharesOneDistribution() {
			assertThat(DealSets.demandScope(all, DemandAxisMode.GROUPED, null))
					.as("묶음이면 색을 가르지 않는다 — 미상도 그대로 표본이다")
					.containsExactly(black, white, unknownColor);
		}

		@Test
		void splitKeepsOnlyTheRequestedValue() {
			assertThat(DealSets.demandScope(all, DemandAxisMode.SPLIT, "블랙")).containsExactly(black);
		}

		@Test
		@DisplayName("분리에서 값 미상 딜은 빠진다 — 아무 분포에나 넣으면 그 분포가 오염된다")
		void splitExcludesUnknownValue() {
			assertThat(DealSets.demandScope(all, DemandAxisMode.SPLIT, "블랙")).doesNotContain(unknownColor);
			assertThat(DealSets.demandScope(all, DemandAxisMode.SPLIT, "화이트")).doesNotContain(unknownColor);
		}

		@Test
		void splitOnAValueNobodyPostedIsEmptyRatherThanEverything() {
			assertThat(DealSets.demandScope(all, DemandAxisMode.SPLIT, "레드")).isEmpty();
		}
	}
}
