package dev.hogumeter.core.application;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.ProductAxisEntity;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 4 알림 배선 — 저장된 기준가·정책 로드 → AlertDispatcher 발송 판정(스텁 발송, 결과만 검증). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EvaluateAlertOnDealUseCaseTest {

	@Autowired
	EvaluateAlertOnDealUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	AlertPolicyRepository policies;
	@Autowired
	PurchaseRepository purchases;
	@Autowired
	ProductAxisRepository productAxes;

	private long variantId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
		// 기준가 SUFFICIENT: 교차검증 5건 {820,850,890,920,950}k → benchmark 890k, P25 850k
		for (long price : new long[] { 820_000, 850_000, 890_000, 920_000, 950_000 }) {
			insertCrossVerifiedDeal(price);
		}
	}

	private void insertCrossVerifiedDeal(long price) {
		Instant when = Instant.now().minus(Duration.ofDays(10));
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + seq++, "https://p.test/" + seq,
				"제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + seq++, "https://r.test/" + seq,
				"제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
	}

	/** Q-48 ② 테스트 전용 — 분리 제품의 특정 축값으로 교차검증 딜을 심는다(setUp의 GROUPED 헬퍼와 별개). */
	private long insertCrossVerifiedSplitDeal(long variantId, long price, String demandAxisValue) {
		Instant when = Instant.now().minus(Duration.ofDays(10));
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "sp" + seq++, "https://p.test/" + seq,
				"제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "sr" + seq++, "https://r.test/" + seq,
				"제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when,
				when, demandAxisValue));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
		return deal.getId();
	}

	private long splitVariantWithBlackDeals() {
		ProductEntity colorProduct = products.save(new ProductEntity("갤럭시 25", "스마트폰", DemandAxisMode.SPLIT));
		long splitVariantId = variants.save(new VariantEntity(colorProduct.getId(), "256GB", Map.of("용량", "256GB")))
				.getId();
		productAxes.save(new ProductAxisEntity(colorProduct.getId(), AxisType.DEMAND, "색상", List.of("블랙", "화이트")));
		for (long price : new long[] { 820_000, 850_000, 890_000, 920_000, 950_000 }) {
			insertCrossVerifiedSplitDeal(splitVariantId, price, "블랙");
		}
		return splitVariantId;
	}

	/**
	 * Q-48 ②(2026-07-30 확정) — 사용자가 "화이트만 알림"으로 필터링해 두면, 데이터가 충분해 원래 SENT할
	 * "블랙" 딜도 조용히 건너뛴다. 필터가 실제로 판정을 좌우하는지 확인하는 테스트다(화이트 딜은 표본이
	 * 아예 없어 filter 유무와 무관하게 NO_ALERT일 수 있으므로, "데이터는 있는데 필터에 안 걸리는" 블랙으로
	 * 검증해야 필터의 효과를 정확히 격리한다).
	 */
	@Test
	void demandAxisFilterSuppressesAlertsForOtherAxisValues() {
		long splitVariantId = splitVariantWithBlackDeals();
		policies.save(new AlertPolicyEntity(splitVariantId, null, 6, null, null, 5, List.of(), List.of("화이트")));

		DispatchOutcome outcome = useCase.evaluate(splitVariantId,
				dealEvents.findByVariantId(splitVariantId).get(0).getId(),
				aDealEvent().withPriceFirst(840_000L).demandAxisValue("블랙").build());

		assertThat(outcome).as("필터(화이트)에 안 걸리는 블랙 딜은 데이터가 충분해도 억제된다")
				.isEqualTo(DispatchOutcome.NO_ALERT);
	}

	/** 필터에 걸리는 축값은 억제되지 않고 평소대로 판정한다(SUFFICIENT 5건 → P25 840k 이하로 SENT). */
	@Test
	void demandAxisFilterAllowsTheMatchingAxisValue() {
		long splitVariantId = splitVariantWithBlackDeals();
		policies.save(new AlertPolicyEntity(splitVariantId, null, 6, null, null, 5, List.of(), List.of("블랙")));

		DispatchOutcome outcome = useCase.evaluate(splitVariantId,
				dealEvents.findByVariantId(splitVariantId).get(0).getId(),
				aDealEvent().withPriceFirst(840_000L).demandAxisValue("블랙").build());

		assertThat(outcome).as("필터에 걸리는 축값은 억제되지 않는다").isEqualTo(DispatchOutcome.SENT);
	}

	@Test
	void goodDealBelowBenchmarkIsSent() {
		policies.save(new AlertPolicyEntity(variantId, 900_000L, 6, null, null, 5, List.of(), List.of()));

		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		DispatchOutcome outcome = useCase.evaluate(variantId, dealId, aDealEvent().withPriceFirst(840_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT); // 840k ≤ P25(850k)=특가
	}

	@Test
	void dealAboveBenchmarkWithoutTargetIsNotSent() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of(), List.of()));

		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		DispatchOutcome outcome = useCase.evaluate(variantId, dealId, aDealEvent().withPriceFirst(950_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.NO_ALERT); // 950k > 기준가 890k, 목표가 없음
	}

	@Test
	void dealBelowActivePurchasePaidPriceFiresPostBuyAlert() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of(), List.of())); // 목표가 없음
		// 활성(OBSERVING) 관찰: 900k에 구매
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, "256GB", 900_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		// 895k: 기준가 890k보다 높아 평소엔 무알림이나, 내 구매가 900k 하회 → PUR-03 산 뒤 알림
		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		DispatchOutcome outcome = useCase.evaluate(variantId, dealId, aDealEvent().withPriceFirst(895_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
	}

	/**
	 * PUR-03: 이 variant의 관찰이 전부 ARCHIVED면 🔥는 꺼진다 — "구매 후 알림을 언제까지 계속할까"의
	 * 마지막 매듭. 배선이 끊기면(주입 무시) 이 테스트가 SENT로 잘못 통과한다.
	 */
	@Test
	void jackpotIsSuppressedWhenAllObservationsAreArchived() {
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, "256GB", 900_000L, Instant.parse("2026-06-01T00:00:00Z"), 90)
						.expire().close().archive(),
				Snapshot.unobserved("P=6mo,K=5")));

		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		// 950k > 기준가 890k·P25 850k라 LOWER 이상치(🔥)가 아니면 아무 것도 안 걸린다.
		DispatchOutcome outcome = useCase.evaluate(variantId, dealId,
				aDealEvent().withPriceFirst(950_000L).outlier(OutlierFlag.LOWER).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.NO_ALERT);
	}

	@Test
	void jackpotIsSentEvenInQuietHours() {
		// 조용시간 전 구간(0~23이 아니라 wrap로 상시) — 🔥 관통 확인
		policies.save(new AlertPolicyEntity(variantId, null, 6, 0, 23, 5, List.of(), List.of()));

		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		DispatchOutcome outcome = useCase.evaluate(variantId, dealId,
				aDealEvent().withPriceFirst(700_000L).outlier(OutlierFlag.LOWER).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
	}
}
