package dev.hogumeter.core.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.IllegalDealTransitionException;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 상태 전이 안전망(Q-84) — DB 갱신 메서드도 {@link DealStatus}의 허용 전이를 지킨다. 지금 이 메서드들의
 * 실제 호출자는 전부 우연히 옳은 전이만 만들지만("우연히 옳은 코드는 다음 커밋에 틀려진다"), 그 우연을
 * 여기서 계약으로 잠근다 — 미래의 호출자가 잘못된 전이를 만들면 조용히 상태가 깨지는 대신 즉시 던진다.
 */
class DealEventEntityTest {

	private static DealEventEntity deal(DealStatus status) {
		Instant t = Instant.parse("2026-07-01T00:00:00Z");
		return new DealEventEntity(1L, false, List.of(), 900_000, 900_000, 900_000, 900_000, Origin.LIVE, false,
				OutlierFlag.NONE, false, status, t, t);
	}

	@Test
	void applyStatusChangeAllowsALegalTransition() {
		DealEventEntity deal = deal(DealStatus.ACTIVE);

		deal.applyStatusChange(DealStatus.ENDED, Instant.now());

		assertThat(deal.getStatus()).isEqualTo(DealStatus.ENDED);
	}

	@Test
	void applyStatusChangeRejectsAnIllegalTransition() {
		DealEventEntity deal = deal(DealStatus.ENDED);

		assertThatThrownBy(() -> deal.applyStatusChange(DealStatus.VERIFIED, Instant.now()))
				.isInstanceOf(IllegalDealTransitionException.class);
	}

	@Test
	void applyMergeAllowsALegalTransition() {
		DealEventEntity deal = deal(DealStatus.ACTIVE);

		deal.applyMerge(900_000, 900_000, 900_000, 900_000, true, DealStatus.VERIFIED, deal.getFirstSeen(),
				Instant.now());

		assertThat(deal.getStatus()).isEqualTo(DealStatus.VERIFIED);
	}

	/**
	 * ReprocessDealPricesUseCase가 실제로 이렇게 부른다(가격만 갱신, 상태는 그대로 되돌려 넣음) — 이 통로가
	 * 막히면 매 가격 갱신마다 예외가 난다.
	 */
	@Test
	void applyMergeAllowsPassingTheSameStatusUnchanged() {
		DealEventEntity deal = deal(DealStatus.VERIFIED);

		deal.applyMerge(900_000, 850_000, 900_000, 850_000, true, DealStatus.VERIFIED, deal.getFirstSeen(),
				Instant.now());

		assertThat(deal.getStatus()).isEqualTo(DealStatus.VERIFIED);
	}

	@Test
	void applyMergeRejectsAnIllegalTransition() {
		DealEventEntity deal = deal(DealStatus.ENDED);

		assertThatThrownBy(() -> deal.applyMerge(900_000, 900_000, 900_000, 900_000, false, DealStatus.VERIFIED,
				deal.getFirstSeen(), Instant.now()))
				.isInstanceOf(IllegalDealTransitionException.class);
	}
}
