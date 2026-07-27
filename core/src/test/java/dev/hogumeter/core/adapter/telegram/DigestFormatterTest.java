package dev.hogumeter.core.adapter.telegram;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.application.AssembleDigestUseCase.Digest;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.application.ComputeDigestBestOpportunityUseCase.BestOpportunity;
import dev.hogumeter.core.application.ComputeDigestOpportunityCountUseCase.OpportunityCount;
import dev.hogumeter.core.application.ComputeDigestTransitionUseCase.DigestTransition;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import dev.hogumeter.core.application.VariantNaming.Naming;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import dev.hogumeter.core.domain.digest.PinDigestEvent.PinDigestEventType;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import dev.hogumeter.core.domain.signal.SignalColor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * DIG-04 렌더링의 순수 계약 — 무변동 합산·조용한 주 축약·큐 병기가 문구로 정직하게 갈리는지 잠근다.
 */
class DigestFormatterTest {

	private final DigestFormatter formatter = new DigestFormatter();
	private static final DigestWindow WINDOW = new DigestWindow(Instant.parse("2026-07-01T00:00:00Z"),
			Instant.parse("2026-07-08T00:00:00Z"));

	private static VariantDigestRow silentRow(long variantId) {
		return new VariantDigestRow(variantId, WINDOW, java.util.Optional.empty(), new OpportunityCount(0, 0),
				new DigestTransition(null, SignalColor.GRAY, false), List.of());
	}

	@Test
	void quietWeekCollapsesToOneLineRegardlessOfRows() {
		Digest digest = new Digest(List.of(silentRow(1)), List.of(), true);

		String out = formatter.format(digest, Map.of());

		assertThat(out).contains("이번 주는 조용했습니다");
		assertThat(out).doesNotContain("최고 기회");
	}

	@Test
	void signaledVariantGetsItsOwnLineAndQuietOnesAreSummed() {
		DealEvent deal = aDealEvent().withPriceFirst(700_000).status(dev.hogumeter.core.domain.deal.DealStatus.ACTIVE)
				.demandAxisValue("블랙").build();
		VariantDigestRow signaled = new VariantDigestRow(1, WINDOW,
				java.util.Optional.of(new BestOpportunity(deal, 700_000, true)), new OpportunityCount(2, 5),
				new DigestTransition(SignalColor.YELLOW, SignalColor.GREEN, true), List.of());
		VariantDigestRow quietA = silentRow(2);
		VariantDigestRow quietB = silentRow(3);
		Naming naming = new Naming("아이폰 17", "256GB");

		String out = formatter.format(new Digest(List.of(signaled, quietA, quietB), List.of(), false),
				Map.of(1L, naming));

		assertThat(out).contains("아이폰 17 256GB");
		assertThat(out).contains("700,000원(basis=블랙)");
		assertThat(out).contains("🟡YELLOW → 🟢GREEN");
		assertThat(out).contains("관찰 이번 창 +2 / 누적 5");
		assertThat(out).contains("나머지 2개 항목은 변동 없음");
	}

	/** ④ 핀 결말 전이 + 부활 이벤트 — 다른 신호가 없어도 이것만으로 자기 줄을 받는다(무변동 합산 대상 아님). */
	@Test
	void pinEventsGiveTheirOwnLineEvenWithoutOtherSignals() {
		VariantDigestRow row = new VariantDigestRow(1, WINDOW, java.util.Optional.empty(), new OpportunityCount(0, 0),
				new DigestTransition(null, SignalColor.GRAY, false),
				List.of(new PinDigestEvent(42L, PinDigestEventType.BOUGHT, Instant.parse("2026-07-03T00:00:00Z"))));

		String out = formatter.format(new Digest(List.of(row), List.of(), false), Map.of());

		assertThat(out).contains("샀어요");
		assertThat(out).doesNotContain("나머지 1개 항목은 변동 없음");
	}

	@Test
	void revivedPinEventRendersDistinctlyFromEndings() {
		VariantDigestRow row = new VariantDigestRow(1, WINDOW, java.util.Optional.empty(), new OpportunityCount(0, 0),
				new DigestTransition(null, SignalColor.GRAY, false),
				List.of(new PinDigestEvent(42L, PinDigestEventType.REVIVED, Instant.parse("2026-07-03T00:00:00Z"))));

		String out = formatter.format(new Digest(List.of(row), List.of(), false), Map.of());

		assertThat(out).contains("다시 살아남");
	}

	@Test
	void unnamedVariantFallsBackToUnknownSubject() {
		DealEvent deal = aDealEvent().build();
		VariantDigestRow row = new VariantDigestRow(9, WINDOW,
				java.util.Optional.of(new BestOpportunity(deal, deal.priceFirst(), true)), new OpportunityCount(1, 1),
				new DigestTransition(null, SignalColor.GRAY, false), List.of());

		String out = formatter.format(new Digest(List.of(row), List.of(), false), Map.of());

		assertThat(out).contains("대상 미상");
	}

	@Test
	void queueSectionCountsItemsAndFlagsUnassignedOnes() {
		PendingItem withSubject = new PendingItem(1, ReviewQueueType.OUTLIER_LOWER, 1, 0, Instant.now(), Instant.now(),
				null, "아이폰 17 — 256GB", List.of(), List.of(), Map.of());
		PendingItem unassigned = new PendingItem(2, ReviewQueueType.UNCLASSIFIED, 1, 0, Instant.now(), Instant.now(),
				null, null, List.of(), List.of(), Map.of());

		String out = formatter.format(new Digest(List.of(), List.of(withSubject, unassigned), true), Map.of());

		assertThat(out).contains("검토 대기 2건");
		assertThat(out).contains("대상 미상 1건 포함");
	}

	/** digestAppearances는 "지금까지 성공한 발송 횟수"라 +1로 이번 발송의 몫을 미리 반영한다. */
	@Test
	void queueSectionShowsTheWorstMissedStreak() {
		PendingItem freshItem = new PendingItem(1, ReviewQueueType.UNCLASSIFIED, 1, 0, Instant.now(), Instant.now(),
				null, null, List.of(), List.of(), Map.of());
		PendingItem repeatOffender = new PendingItem(2, ReviewQueueType.OUTLIER_LOWER, 1, 3, Instant.now(),
				Instant.now(), null, "아이폰 17 — 256GB", List.of(), List.of(), Map.of());

		String out = formatter.format(new Digest(List.of(), List.of(freshItem, repeatOffender), true), Map.of());

		assertThat(out).contains("최다 4회째 미확인"); // 3(이미 성공) + 1(이번 발송)
	}

	/** 전부 첫 등장(0회)이면 "N회째"는 잡음이라 안 낸다. */
	@Test
	void queueSectionOmitsTheStreakLineWhenEveryItemIsFirstSeen() {
		PendingItem freshItem = new PendingItem(1, ReviewQueueType.UNCLASSIFIED, 1, 0, Instant.now(), Instant.now(),
				null, null, List.of(), List.of(), Map.of());

		String out = formatter.format(new Digest(List.of(), List.of(freshItem), true), Map.of());

		assertThat(out).doesNotContain("회째");
	}

	@Test
	void emptyQueueSaysSo() {
		String out = formatter.format(new Digest(List.of(), List.of(), true), Map.of());

		assertThat(out).contains("검토 대기: 없음");
	}
}
