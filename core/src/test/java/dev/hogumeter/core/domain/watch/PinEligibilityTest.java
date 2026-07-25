package dev.hogumeter.core.domain.watch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import org.junit.jupiter.api.Test;

/**
 * WATCH(docs/17) 핀 자격 = [표시된 딜 ∧ ENDED 아님 ∧ 자격 상실 아님 ∧ 기각 아님]. 화면에 안 뜨는 딜을
 * 핀 하는 것은 말이 안 된다 — outlierFlag≠NONE(이상치 표본 제외)·permanentlyExcluded(사기 기각)는
 * 이미 "화면에 안 뜨는 딜"의 정본 조건이라 그대로 재사용한다(사본 아님, DealEvent 필드 그대로 읽는다).
 */
class PinEligibilityTest {

	@Test
	void anActiveNonOutlierNonExcludedDealCanBePinned() {
		assertThat(PinEligibility.canPin(DealStatus.ACTIVE, OutlierFlag.NONE, false)).isTrue();
	}

	@Test
	void aVerifiedDealCanBePinnedToo() {
		assertThat(PinEligibility.canPin(DealStatus.VERIFIED, OutlierFlag.NONE, false)).isTrue();
	}

	@Test
	void anEndedDealCannotBePinned() {
		assertThat(PinEligibility.canPin(DealStatus.ENDED, OutlierFlag.NONE, false)).isFalse();
	}

	@Test
	void anOutlierFlaggedDealCannotBePinned() {
		assertThat(PinEligibility.canPin(DealStatus.ACTIVE, OutlierFlag.LOWER, false)).isFalse();
		assertThat(PinEligibility.canPin(DealStatus.ACTIVE, OutlierFlag.UPPER, false)).isFalse();
	}

	@Test
	void aPermanentlyExcludedDealCannotBePinned() {
		assertThat(PinEligibility.canPin(DealStatus.ACTIVE, OutlierFlag.NONE, true)).isFalse();
	}
}
