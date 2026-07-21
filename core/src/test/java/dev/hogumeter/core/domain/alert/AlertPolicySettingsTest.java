package dev.hogumeter.core.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * REG-03 알림 정책의 순수 검증. DB의 CHECK 제약(quiet hours 0~23)에 걸리면 500이 나간다 —
 * 클라이언트가 무엇을 잘못했는지 알 수 없다. 그래서 도메인이 먼저 거절한다.
 */
class AlertPolicySettingsTest {

	@Test
	void targetPriceIsOptional() {
		AlertPolicySettings settings = new AlertPolicySettings(null, 6, null, null, 5, List.of());

		assertThat(settings.targetPrice()).isNull();
	}

	/** "목표가 0원"은 목표가 미설정이 아니라 "공짜여야 알림"이다. 둘을 섞으면 알림이 조용히 죽는다. */
	@ParameterizedTest
	@ValueSource(longs = { 0L, -1L })
	void targetPriceMustBePositiveWhenPresent(long targetPrice) {
		assertThatThrownBy(() -> new AlertPolicySettings(targetPrice, 6, null, null, 5, List.of()))
			.isInstanceOf(InvalidAlertPolicyException.class);
	}

	/** 기간 P는 기준가 계산에 그대로 들어간다 — 코드도 `BM_INVALID_PERIOD`를 재사용한다. */
	@ParameterizedTest
	@ValueSource(ints = { 0, -3 })
	void periodMonthsMustBePositive(int periodMonths) {
		assertThatThrownBy(() -> new AlertPolicySettings(null, periodMonths, null, null, 5, List.of()))
			.isInstanceOf(InvalidBenchmarkPeriodException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 24 })
	void quietHoursMustBeAnHourOfDay(int hour) {
		assertThatThrownBy(() -> new AlertPolicySettings(null, 6, hour, 8, 5, List.of()))
			.isInstanceOf(InvalidAlertPolicyException.class);
		assertThatThrownBy(() -> new AlertPolicySettings(null, 6, 8, hour, 5, List.of()))
			.isInstanceOf(InvalidAlertPolicyException.class);
	}

	/** 한쪽만 설정하면 {@link QuietHours}는 조용히 "방해금지 없음"으로 읽는다 — 설정한 줄 알고 있는데. */
	@Test
	void quietHoursAreSetTogetherOrNotAtAll() {
		assertThatThrownBy(() -> new AlertPolicySettings(null, 6, 23, null, 5, List.of()))
			.isInstanceOf(InvalidAlertPolicyException.class);
		assertThatThrownBy(() -> new AlertPolicySettings(null, 6, null, 8, 5, List.of()))
			.isInstanceOf(InvalidAlertPolicyException.class);
	}

	/** start == end는 {@link QuietHours}가 "방해금지 없음"으로 정의한 값이다. 오류가 아니다. */
	@Test
	void equalQuietHoursMeanNoQuietWindowAndThatIsLegal() {
		assertThatCode(() -> new AlertPolicySettings(null, 6, 9, 9, 5, List.of())).doesNotThrowAnyException();
	}

	@Test
	void midnightWrapIsLegal() {
		AlertPolicySettings settings = new AlertPolicySettings(900_000L, 6, 23, 8, 5, List.of());

		assertThat(QuietHours.isQuiet(2, settings.quietHoursStart(), settings.quietHoursEnd())).isTrue();
		assertThat(QuietHours.isQuiet(12, settings.quietHoursStart(), settings.quietHoursEnd())).isFalse();
	}

	/** null 목록은 빈 목록으로 — 미설정과 "없음"을 같게 다뤄야 컬럼(NOT NULL default '{}')과 어긋나지 않는다. */
	@Test
	void nullExcludeKeywordsBecomeEmptyList() {
		assertThat(new AlertPolicySettings(null, 6, null, null, 5, null).excludeKeywords()).isEmpty();
	}

	/** Q-28: 제외 키워드는 저장 전에 공백 제거·빈 값 탈락·중복 접기로 정규화한다 — 사용자 입력의 지저분함을 컬럼에 굳히지 않는다. */
	@Test
	void excludeKeywordsAreTrimmedDedupedAndBlanksDropped() {
		AlertPolicySettings settings = new AlertPolicySettings(null, 6, null, null, 5,
				List.of("  리퍼 ", "리퍼", "  ", "벌크"));

		assertThat(settings.excludeKeywords()).containsExactly("리퍼", "벌크");
	}
}
