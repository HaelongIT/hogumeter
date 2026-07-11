package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** USED-02 신규 매물 알림 판정(docs/used/04 AC-7) — 순수. */
class UsedAlertPolicyTest {

	private static final UsedSearchSpec SPEC = new UsedSearchSpec(List.of("아이폰17"),
			List.of(new BonusGroup(List.of("미개봉"), BonusMode.TRIGGER)), List.of());

	@Test
	void alertsWhenFilterPassesAndUnderTarget() {
		assertThat(UsedAlertPolicy.shouldAlertOnNew("아이폰17 256 미개봉", 800_000, SPEC, 850_000L)).isTrue();
	}

	@Test
	void priceAtTargetStillAlerts() {
		// 경계: targetPrice 이하 = 목표가와 같아도 알림
		assertThat(UsedAlertPolicy.shouldAlertOnNew("아이폰17 256 미개봉", 850_000, SPEC, 850_000L)).isTrue();
	}

	@Test
	void doesNotAlertAboveTarget() {
		assertThat(UsedAlertPolicy.shouldAlertOnNew("아이폰17 256 미개봉", 900_000, SPEC, 850_000L)).isFalse();
	}

	@Test
	void nullTargetIgnoresPrice() {
		// 목표가 미설정 → 필터 통과만으로 알림(가격 무관)
		assertThat(UsedAlertPolicy.shouldAlertOnNew("아이폰17 256 미개봉", 9_999_999, SPEC, null)).isTrue();
	}

	@Test
	void doesNotAlertWhenTriggerMissing() {
		// required는 통과하나 TRIGGER(미개봉) 없음 → 알림 안 함(가격이 아무리 좋아도)
		assertThat(UsedAlertPolicy.shouldAlertOnNew("아이폰17 256 S급", 1, SPEC, 850_000L)).isFalse();
	}

	@Test
	void doesNotAlertWhenRequiredMissing() {
		assertThat(UsedAlertPolicy.shouldAlertOnNew("갤럭시 256 미개봉", 1, SPEC, 850_000L)).isFalse();
	}

	// AC-8: 승격 매물의 가격 하락만 후속 알림
	@Test
	void followsUpOnPriceDropOnlyWhenPromoted() {
		PriceChange drop = new PriceChange("A", 900_000, 850_000);
		PriceChange rise = new PriceChange("A", 850_000, 900_000);

		assertThat(UsedAlertPolicy.shouldAlertOnPriceChange(drop, true)).isTrue();
		assertThat(UsedAlertPolicy.shouldAlertOnPriceChange(drop, false)).isFalse(); // 미승격
		assertThat(UsedAlertPolicy.shouldAlertOnPriceChange(rise, true)).isFalse(); // 상승은 배지만
	}

	// AC-9: 승격 매물의 판매완료만 알림
	@Test
	void followsUpOnSoldOutOnlyWhenPromoted() {
		assertThat(UsedAlertPolicy.shouldAlertOnSoldOut(true)).isTrue();
		assertThat(UsedAlertPolicy.shouldAlertOnSoldOut(false)).isFalse();
	}
}
