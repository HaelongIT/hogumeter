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
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Duration;
import java.time.Instant;
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

	private long variantId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.SPLIT));
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

	@Test
	void goodDealBelowBenchmarkIsSent() {
		policies.save(new AlertPolicyEntity(variantId, 900_000L, 6, null, null));

		DispatchOutcome outcome = useCase.evaluate(variantId, aDealEvent().withPriceFirst(840_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT); // 840k ≤ P25(850k)=특가
	}

	@Test
	void dealAboveBenchmarkWithoutTargetIsNotSent() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null));

		DispatchOutcome outcome = useCase.evaluate(variantId, aDealEvent().withPriceFirst(950_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.NO_ALERT); // 950k > 기준가 890k, 목표가 없음
	}

	@Test
	void dealBelowActivePurchasePaidPriceFiresPostBuyAlert() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null)); // 목표가 없음
		// 활성(OBSERVING) 관찰: 900k에 구매
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, "256GB", 900_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		// 895k: 기준가 890k보다 높아 평소엔 무알림이나, 내 구매가 900k 하회 → PUR-03 산 뒤 알림
		DispatchOutcome outcome = useCase.evaluate(variantId, aDealEvent().withPriceFirst(895_000L).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
	}

	@Test
	void jackpotIsSentEvenInQuietHours() {
		// 조용시간 전 구간(0~23이 아니라 wrap로 상시) — 🔥 관통 확인
		policies.save(new AlertPolicyEntity(variantId, null, 6, 0, 23));

		DispatchOutcome outcome = useCase.evaluate(variantId,
				aDealEvent().withPriceFirst(700_000L).outlier(OutlierFlag.LOWER).build());

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
	}
}
