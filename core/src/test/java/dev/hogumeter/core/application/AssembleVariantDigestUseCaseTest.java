package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import dev.hogumeter.core.domain.digest.PinDigestEvent.PinDigestEventType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.signal.SignalColor;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 조립 배선 — 섹션 유스케이스들이 <b>같은 창</b>으로 한 행에 묶이는지 검증한다(각 섹션의 계산은
 * 이미 각자의 테스트가 잠갔다). 창이 섹션마다 갈리지 않는다는 것이 이 계약의 핵심이다.
 *
 * <p>창 계산({@link ComputeDigestWindowUseCase})만 실 시계를 타므로 — 활성 시각(product.created_at)이
 * ≈now라 자동주입 시계로는 창이 0폭이 된다 — 그 유스케이스만 <b>고정 시계</b>로 손수 조립한다. 나머지
 * 세 섹션은 창을 파라미터로 받아 시계와 무관하므로 자동주입분을 그대로 쓴다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AssembleVariantDigestUseCaseTest {

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DigestStateRepository digestStates;
	@Autowired
	ComputeDigestBestOpportunityUseCase bestOpportunities;
	@Autowired
	ComputeDigestOpportunityCountUseCase opportunityCounts;
	@Autowired
	ComputeDigestTransitionUseCase transitions;
	@Autowired
	ComputeDigestPinEndingsUseCase pinEndings;
	@Autowired
	WatchItemRepository watchItems;

	private AssembleVariantDigestUseCase withFixedSendTime(Instant thisSend) {
		ComputeDigestWindowUseCase windows = new ComputeDigestWindowUseCase(variants, products, digestStates,
				Clock.fixed(thisSend, ZoneOffset.UTC));
		return new AssembleVariantDigestUseCase(windows, bestOpportunities, opportunityCounts, transitions, pinEndings);
	}

	@Test
	void assemblesEverySectionForAVariantUnderOneWindow() {
		ProductEntity product = products.save(new ProductEntity("조립 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		long v = variant.getId();
		Instant lastSent = product.getCreatedAt().plusSeconds(3600);
		digestStates.save(new DigestStateEntity(v, lastSent, "GREEN", "NO_ACTIVE_DEAL", "GROUPED"));
		Instant thisSend = lastSent.plusSeconds(2 * 24 * 3600); // 발송 = 창 시작 이틀 뒤
		Instant inWindow = lastSent.plusSeconds(24 * 3600); // 창 [lastSent, thisSend) 안
		dealEvents.save(new DealEventEntity(v, false, null, 880_000, 880_000, 880_000, 880_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, inWindow, inWindow));

		VariantDigestRow row = withFixedSendTime(thisSend).assemble(v);

		assertThat(row.variantId()).isEqualTo(v);
		assertThat(row.window().from()).isEqualTo(lastSent); // 창 시작 = 직전 발송
		assertThat(row.window().to()).isEqualTo(thisSend);
		// 세 섹션이 모두 같은 창(row.window)을 봤다:
		assertThat(row.bestOpportunity()).isPresent();
		assertThat(row.bestOpportunity().orElseThrow().opportunityPrice()).isEqualTo(880_000);
		assertThat(row.observation().inWindow()).isEqualTo(1);
		// 저장 색 GREEN vs 현재 색(딜 1건이라 GRAY/SPARSE 계열) → 전환 보고 대상
		assertThat(row.transition().from()).isEqualTo(SignalColor.GREEN);
		assertThat(row.transition().reportable()).isTrue();
		assertThat(row.pinEvents()).isEmpty(); // 핀 이력 없음
	}

	@Test
	void emptyVariantAssemblesAllZeroSections() {
		ProductEntity product = products.save(new ProductEntity("빈 조립", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		Instant thisSend = product.getCreatedAt().plusSeconds(7 * 24 * 3600);

		VariantDigestRow row = withFixedSendTime(thisSend).assemble(variant.getId());

		assertThat(row.bestOpportunity()).isEmpty();
		assertThat(row.observation().inWindow()).isZero();
		assertThat(row.observation().cumulative()).isZero();
		assertThat(row.transition().from()).isNull(); // 발송 이력 없음 → 기준선
		assertThat(row.transition().reportable()).isFalse();
		assertThat(row.pinEvents()).isEmpty();
	}

	/** ④가 다른 세 섹션과 같은 창으로 조립되는지 — 창 밖 결말은 담기지 않는다. */
	@Test
	void pinEndingWithinWindowFlowsIntoTheAssembledRow() {
		ProductEntity product = products.save(new ProductEntity("핀 조립 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		long v = variant.getId();
		Instant lastSent = product.getCreatedAt().plusSeconds(3600);
		digestStates.save(new DigestStateEntity(v, lastSent, "GREEN", "NO_ACTIVE_DEAL", "GROUPED"));
		Instant thisSend = lastSent.plusSeconds(2 * 24 * 3600);
		Instant inWindow = lastSent.plusSeconds(24 * 3600);
		DealEventEntity deal = dealEvents.save(new DealEventEntity(v, false, null, 880_000, 880_000, 880_000, 880_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, inWindow, inWindow));
		WatchItemEntity pin = watchItems.save(new WatchItemEntity(deal.getId(), null, null));
		pin.resolve(PinState.BOUGHT, inWindow);
		watchItems.save(pin);

		VariantDigestRow row = withFixedSendTime(thisSend).assemble(v);

		assertThat(row.pinEvents()).extracting(PinDigestEvent::type).containsExactly(PinDigestEventType.BOUGHT);
	}
}
