package dev.hogumeter.core.domain.deal;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** BM-05 AC-3 승격/기각(판단은 사람). 승격 → 이상치 해제·복귀 / 기각 → 영구 제외. */
class DealEventOutlierTransitionTest {

	@Test
	void flagOutlierSetsFlag() {
		assertThat(aDealEvent().build().flagOutlier(OutlierFlag.LOWER).outlierFlag()).isEqualTo(OutlierFlag.LOWER);
	}

	@Test
	void promoteFromOutlierClearsFlagForReinclusion() {
		DealEvent promoted = aDealEvent().outlier(OutlierFlag.LOWER).build().promoteFromOutlier();

		assertThat(promoted.outlierFlag()).isEqualTo(OutlierFlag.NONE);
		assertThat(promoted.permanentlyExcluded()).isFalse();
	}

	@Test
	void rejectMarksPermanentlyExcluded() {
		DealEvent rejected = aDealEvent().outlier(OutlierFlag.LOWER).build().reject();

		assertThat(rejected.permanentlyExcluded()).isTrue();
	}
}
