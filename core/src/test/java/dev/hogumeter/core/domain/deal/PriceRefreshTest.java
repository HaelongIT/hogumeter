package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BM-01 AC-2 "가격 변경은 기존 행에 반영" — 순수 산술. (docs/91 Q-27 ①)
 *
 * <p>가격 역할 3분법(v1.3, docs/02:19): {@code priceFirst}=발생·분포(불변) / {@code priceLast}="지금" /
 * {@code priceMin}="지나간 기회" / {@code priceMax}=역할 없음.
 */
class PriceRefreshTest {

	private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

	private static DealEvent deal(long first, long min, long max, long last, Instant lastSeen) {
		return new DealEvent(1L, false, Set.of(), first, min, max, last, Origin.LIVE, Set.of("ppomppu"),
				OutlierFlag.NONE, false, DealStatus.ACTIVE, T0, lastSeen, "ppomppu", "https://x/1", Set.of());
	}

	private static PriceEvidence evidence(long price, long plusHours, boolean active) {
		return new PriceEvidence(price, T0.plusSeconds(plusHours * 3600), active);
	}

	@Test
	@DisplayName("가격이 내렸다 — priceLast는 지금 값, priceMin은 새 최저, priceFirst는 건드리지 않는다")
	void priceDropped() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(evidence(900_000, 1, true))).orElseThrow();

		assertThat(refresh.priceLast()).isEqualTo(900_000);
		assertThat(refresh.priceMin()).isEqualTo(900_000);
		assertThat(refresh.priceMax()).isEqualTo(999_000);
		assertThat(refresh.lastSeen()).isEqualTo(T0.plusSeconds(3600));
	}

	@Test
	@DisplayName("가격이 올랐다 — priceLast만 오르고 priceMin은 지나간 기회를 기억한다")
	void priceRose() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 900_000, 999_000, 900_000, T0),
				List.of(evidence(1_100_000, 1, true))).orElseThrow();

		assertThat(refresh.priceLast()).isEqualTo(1_100_000);
		assertThat(refresh.priceMin()).isEqualTo(900_000);
		assertThat(refresh.priceMax()).isEqualTo(1_100_000);
	}

	@Test
	@DisplayName("아무것도 안 바뀌었으면 쓰지 않는다 — 빈 갱신은 lastSeen만 흔든다")
	void noChangeMeansNoWrite() {
		Optional<PriceRefresh> refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(new PriceEvidence(999_000, T0, true)));

		assertThat(refresh).isEmpty();
	}

	@Test
	@DisplayName("여러 소스 — 가장 최근에 관측된 **활성** 원문의 가격이 \"지금\"이다")
	void newestActiveEvidenceWins() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(evidence(950_000, 1, true), evidence(930_000, 3, true), evidence(940_000, 2, true)))
				.orElseThrow();

		assertThat(refresh.priceLast()).isEqualTo(930_000);
		assertThat(refresh.priceMin()).isEqualTo(930_000);
	}

	@Test
	@DisplayName("품절된 원문은 \"지금\"의 후보가 아니다 — 그래도 \"지나간 기회\"에는 남는다")
	void endedEvidenceCannotBeTheCurrentPrice() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(evidence(950_000, 1, true), evidence(800_000, 5, false))).orElseThrow();

		assertThat(refresh.priceLast()).as("가장 최근이지만 품절이라 지금 살 수 없다").isEqualTo(950_000);
		assertThat(refresh.priceMin()).as("한때 800,000원에 살 수 있었다").isEqualTo(800_000);
	}

	@Test
	@DisplayName("모든 원문이 품절이면 priceLast를 건드리지 않는다 (종료 판정은 다른 유스케이스의 몫)")
	void allEndedKeepsPriceLast() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(evidence(800_000, 5, false))).orElseThrow();

		assertThat(refresh.priceLast()).isEqualTo(999_000);
		assertThat(refresh.priceMin()).isEqualTo(800_000);
	}

	@Test
	@DisplayName("lastSeen은 뒤로 가지 않는다 (단조) — 늦게 도착한 과거 관측이 시계를 되돌리면 안 된다")
	void lastSeenIsMonotonic() {
		Instant later = T0.plusSeconds(10 * 3600);
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, later),
				List.of(evidence(900_000, 1, true))).orElseThrow();

		assertThat(refresh.lastSeen()).isEqualTo(later);
	}

	@Test
	@DisplayName("증거가 없으면 갱신도 없다")
	void noEvidenceNoRefresh() {
		assertThat(PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0), List.of())).isEmpty();
	}

	@Test
	@DisplayName("동시각 활성 관측이 여럿이면 더 싼 값을 \"지금\"으로 본다 (사용자에게 유리한 쪽)")
	void tieBreaksToTheCheaperPrice() {
		PriceRefresh refresh = PriceRefresh.from(deal(999_000, 999_000, 999_000, 999_000, T0),
				List.of(evidence(950_000, 1, true), evidence(940_000, 1, true))).orElseThrow();

		assertThat(refresh.priceLast()).isEqualTo(940_000);
	}
}
