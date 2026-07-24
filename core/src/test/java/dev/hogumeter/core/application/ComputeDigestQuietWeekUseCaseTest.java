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
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 ⑥ 조용한 주 배선 — 전 플로우 0 ∧ 전환 0 ∧ 공백 없음(현재 고정). 여러 유스케이스를 조합해
 * {@link dev.hogumeter.core.domain.digest.DigestRules#isQuietWeek}에 넘기는 <b>주입</b>을 시험한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestQuietWeekUseCaseTest {

	@Autowired
	ComputeDigestQuietWeekUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DigestStateRepository digestStates;

	/** 발송 이력을 넣어 창을 좁힌다(창 시작을 lastSent로) — 안 넣으면 창이 product 등록 시각부터라 과거 딜까지 창 안이 된다. */
	private long newVariantWithPriorSend(Instant lastSent) {
		ProductEntity product = products.save(new ProductEntity("조용한주 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		digestStates.save(new DigestStateEntity(variant.getId(), lastSent, "GRAY", "NO_ACTIVE_DEAL", "GROUPED"));
		return variant.getId();
	}

	private void insertDealNow(long variantId) {
		Instant recent = Instant.now(); // 창 [lastSent(과거), now+) 안에 들도록
		dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, recent, recent));
	}

	@Test
	void noFlowNoTransitionIsAQuietWeek() {
		// 저장 색 GRAY = 현재 색(딜 없음 → GRAY)과 같아 전환 없음. 딜 없음 → 플로우 0. 공백은 고정 false.
		long v = newVariantWithPriorSend(Instant.now().minusSeconds(7 * 24 * 3600));

		assertThat(useCase.isQuietWeek(List.of(v))).isTrue();
	}

	@Test
	void aFlowInAnyVariantBreaksTheQuietWeek() {
		long v = newVariantWithPriorSend(Instant.now().minusSeconds(7 * 24 * 3600));
		insertDealNow(v); // 창 안 딜 → 플로우 있음

		assertThat(useCase.isQuietWeek(List.of(v))).isFalse();
	}

	@Test
	void aTransitionInAnyVariantBreaksTheQuietWeek() {
		// 저장 색을 GREEN으로 두면 현재(GRAY)와 달라 전환 발생 → 조용한 주 아님.
		ProductEntity product = products.save(new ProductEntity("전환 있음", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		digestStates.save(new DigestStateEntity(variant.getId(), Instant.now().minusSeconds(7 * 24 * 3600),
				"GREEN", "NO_ACTIVE_DEAL", "GROUPED"));

		assertThat(useCase.isQuietWeek(List.of(variant.getId()))).isFalse();
	}

	@Test
	void quietOnlyIfEveryVariantIsQuiet() {
		long quiet = newVariantWithPriorSend(Instant.now().minusSeconds(7 * 24 * 3600));
		long loud = newVariantWithPriorSend(Instant.now().minusSeconds(7 * 24 * 3600));
		insertDealNow(loud);

		assertThat(useCase.isQuietWeek(List.of(quiet, loud))).isFalse();
	}
}
