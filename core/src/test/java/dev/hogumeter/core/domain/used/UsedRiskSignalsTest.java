package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** USED-04 위험 신호 — 나열만, 판정 없음(docs/used/04 AC-13·14). 순수. */
class UsedRiskSignalsTest {

	private static final List<String> REPERTOIRE = List.of("이민 급처", "선입금", "직거래만");

	// AC-13/14: 업자 레퍼토리 키워드 히트를 나열
	@Test
	void listsRepertoireKeywordHits() {
		List<RiskSignal> signals = UsedRiskSignals.detect("이민 급처 선입금만 받아요", REPERTOIRE, 500_000, null, 30);

		assertThat(signals).extracting(RiskSignal::detail).contains("이민 급처", "선입금");
		assertThat(signals).allSatisfy(s -> assertThat(s.category()).isEqualTo(UsedRiskSignals.CATEGORY_REPERTOIRE));
	}

	// AC-14: 판정 문구를 쓰지 않는다 — 결론은 사람
	@Test
	void neverAsserts() {
		List<RiskSignal> signals = UsedRiskSignals.detect("이민 급처 선입금", REPERTOIRE, 100_000, 900_000L, 30);

		assertThat(signals).isNotEmpty();
		assertThat(signals).allSatisfy(s -> {
			assertThat(s.category() + s.detail()).doesNotContain("사기", "위험", "안전", "정상");
		});
	}

	// AC-13: 스냅샷 최저 대비 과도하게 저렴하면 가격 플래그
	@Test
	void flagsAbnormallyCheapVsSnapshotLowest() {
		// 최저 900k 대비 30% 이상 저렴(=630k 이하)인 100k → 플래그
		List<RiskSignal> signals = UsedRiskSignals.detect("아이폰17 급처", List.of(), 100_000, 900_000L, 30);

		assertThat(signals).extracting(RiskSignal::category).contains(UsedRiskSignals.CATEGORY_PRICE);
	}

	@Test
	void noPriceFlagWhenWithinRange() {
		// 최저 900k 대비 30% 미만 저렴(=700k)는 플래그 안 함
		List<RiskSignal> signals = UsedRiskSignals.detect("아이폰17", List.of(), 700_000, 900_000L, 30);

		assertThat(signals).isEmpty();
	}

	@Test
	void noSnapshotLowestSkipsPriceFlag() {
		List<RiskSignal> signals = UsedRiskSignals.detect("아이폰17", List.of(), 1, null, 30);

		assertThat(signals).isEmpty();
	}

	@Test
	void normalizesKeywordMatch() {
		// 공백·대소문자 무관 — "이민급처"(붙여쓰기)도 히트
		List<RiskSignal> signals = UsedRiskSignals.detect("이민급처합니다", REPERTOIRE, 500_000, null, 30);

		assertThat(signals).extracting(RiskSignal::detail).contains("이민 급처");
	}
}
