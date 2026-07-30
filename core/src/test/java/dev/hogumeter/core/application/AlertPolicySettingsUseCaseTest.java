package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.domain.alert.AlertPolicySettings;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * REG-03 알림 정책 쓰기. <b>이 유스케이스가 없던 동안 `alert_policy`에는 프로덕션 writer가 없었다</b> —
 * `EvaluateAlertOnDealUseCase`가 읽기는 했지만 행이 영원히 없었으므로 목표가 트리거·방해금지는
 * 발화할 수 없었다. 테스트만 `policies.save(...)`로 손수 행을 넣고 GREEN이었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AlertPolicySettingsUseCaseTest {

	@Autowired
	AlertPolicySettingsUseCase settings;
	@Autowired
	RegisterProductUseCase registerProduct;
	@Autowired
	GetProductsUseCase getProducts;
	@Autowired
	AlertPolicyRepository policies;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	EntityManager em;

	private long variantId() {
		long productId = registerProduct.register(new RegisterProductCommand("아이폰 17", "phone",
				DemandAxisMode.GROUPED,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))),
				List.of("아이폰17")));
		em.flush();
		return getProducts.variantsOf(productId).get(0).variantId();
	}

	@Test
	void unconfiguredVariantHasNoPolicyRatherThanADefaultOne() {
		assertThat(settings.get(variantId())).isEmpty();
	}

	@Test
	void updateCreatesThePolicyAndGetReadsItBack() {
		long variantId = variantId();

		settings.update(variantId, new AlertPolicySettings(900_000L, 3, 23, 8, 5, List.of(), List.of("블랙")));

		AlertPolicySettings stored = settings.get(variantId).orElseThrow();
		assertThat(stored.targetPrice()).isEqualTo(900_000L);
		assertThat(stored.periodMonths()).isEqualTo(3);
		assertThat(stored.quietHoursStart()).isEqualTo(23);
		assertThat(stored.quietHoursEnd()).isEqualTo(8);
		assertThat(stored.demandAxisFilter()).containsExactly("블랙");
	}

	/** `variant_id`는 UNIQUE다. 두 번째 저장이 insert면 제약 위반으로 500이 난다. */
	@Test
	void updatingTwiceReplacesTheRowRatherThanAddingOne() {
		long variantId = variantId();

		settings.update(variantId, new AlertPolicySettings(900_000L, 3, null, null, 5, List.of(), List.of()));
		settings.update(variantId, new AlertPolicySettings(800_000L, 6, null, null, 5, List.of(), List.of()));

		assertThat(settings.get(variantId).orElseThrow().targetPrice()).isEqualTo(800_000L);
		assertThat(policies.findAll().stream().filter(p -> p.getVariantId() == variantId)).hasSize(1);
	}

	@Test
	void clearingTheTargetPriceIsPersistedAsAbsence() {
		long variantId = variantId();
		settings.update(variantId, new AlertPolicySettings(900_000L, 6, null, null, 5, List.of(), List.of()));

		settings.update(variantId, new AlertPolicySettings(null, 6, null, null, 5, List.of(), List.of()));

		assertThat(settings.get(variantId).orElseThrow().targetPrice()).isNull();
	}

	/**
	 * {@code AlertPolicyEntity}는 이제 {@code alert_policy}의 전 컬럼을 매핑한다(2026-07-30,
	 * {@code demand_axis_filter}가 마지막이었다 — Q-48). 갱신이 벌크 UPDATE인 이유(엔티티에 setter가
	 * 없어 더티 체킹 불가·미매핑 컬럼 보존)는 남아 있으므로, 이 테스트는 이제 "보존"이 아니라 세 컬럼
	 * (k_display·exclude_keywords·demand_axis_filter) 전부가 실제로 **갱신**되는지를 확인한다.
	 */
	@Test
	void updateWritesAllMappedColumns() {
		long variantId = variantId();
		settings.update(variantId,
				new AlertPolicySettings(900_000L, 3, null, null, 9, List.of("리퍼", "벌크"), List.of("블랙")));

		settings.update(variantId,
				new AlertPolicySettings(900_000L, 3, null, null, 7, List.of("정가", "해외"), List.of("화이트")));

		String keywords = jdbc.queryForObject(
				"select array_to_string(exclude_keywords, ',') from alert_policy where variant_id = ?",
				String.class, variantId);
		String demandFilter = jdbc.queryForObject(
				"select demand_axis_filter::text from alert_policy where variant_id = ?", String.class, variantId);
		Integer kDisplay = jdbc.queryForObject(
				"select k_display from alert_policy where variant_id = ?", Integer.class, variantId);

		assertThat(keywords).as("exclude_keywords 갱신(Q-28)").isEqualTo("정가,해외");
		assertThat(demandFilter).as("demand_axis_filter 갱신(Q-48)").contains("화이트").doesNotContain("블랙");
		assertThat(kDisplay).as("k_display 갱신").isEqualTo(7);
	}

	/** FK 위반으로 500을 내지 않는다. "없는 variant"는 클라이언트 오류(404)다. */
	@Test
	void updatingAnUnknownVariantIsNotFoundRatherThanAServerError() {
		assertThatThrownBy(
				() -> settings.update(999_999L, new AlertPolicySettings(null, 6, null, null, 5, List.of(), List.of())))
			.isInstanceOf(VariantNotFoundException.class);
	}

	@Test
	void readingAnUnknownVariantIsNotFound() {
		assertThatThrownBy(() -> settings.get(999_999L)).isInstanceOf(VariantNotFoundException.class);
	}
}
