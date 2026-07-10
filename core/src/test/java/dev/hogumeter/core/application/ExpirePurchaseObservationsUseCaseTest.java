package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-01 관찰 만료. <b>이 유스케이스가 없던 동안 관찰은 영원히 끝나지 않았다</b> —
 * {@code Purchase.expire()}·{@code isExpired()}는 순수 도메인에 있고 테스트도 GREEN이었지만,
 * 프로덕션에서 {@code purchase.state}를 쓰는 곳은 {@code RecordPurchaseUseCase} 하나뿐이고
 * 언제나 {@code OBSERVING}을 썼다.
 *
 * <p>그 결과 ① 90일이 지나도 "관찰 N일차"가 무한히 커지고 ② PUR-03 "산 뒤 알림"(paidPrice 하회)이
 * OBSERVING에만 발화하므로 <b>3년 전 구매에 대해서도 계속 알림이 나갔을 것</b>이며 ③ 성적표 대기
 * (REPORT_PENDING)로 넘어가지 않는다.
 *
 * <p>@Transactional로 tx 롤백. 컨테이너 공유라 삽입한 id로 스코프한다(docs/99).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ExpirePurchaseObservationsUseCaseTest {

	@Autowired
	ExpirePurchaseObservationsUseCase expireObservations;
	@Autowired
	RegisterProductUseCase registerProduct;
	@Autowired
	GetProductsUseCase getProducts;
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

	/** `purchasedAt`을 과거로 밀어 만료를 만든다 — 테스트가 90일을 기다릴 수는 없다. */
	private long purchase(long variantId, Duration ago, int observationDays, String state) {
		return jdbc.queryForObject("""
				insert into purchase (variant_id, paid_price, purchased_at, observation_days, state,
				                      snap_benchmark_price, snap_tier, snap_n, snap_m, snap_unobserved)
				values (?, 899000, ?::timestamptz, ?, ?, 820000, 'SUFFICIENT', 12, 3, false) returning id
				""", Long.class, variantId, Instant.now().minus(ago).toString(), observationDays, state);
	}

	private String stateOf(long purchaseId) {
		return jdbc.queryForObject("select state from purchase where id = ?", String.class, purchaseId);
	}

	@Test
	void observationPastItsEndMovesToReportPending() {
		long id = purchase(variantId(), Duration.ofDays(91), 90, "OBSERVING");

		int expired = expireObservations.expireDueObservations();

		assertThat(expired).isEqualTo(1);
		assertThat(stateOf(id)).isEqualTo("REPORT_PENDING");
	}

	@Test
	void observationStillRunningIsLeftAlone() {
		long id = purchase(variantId(), Duration.ofDays(89), 90, "OBSERVING");

		assertThat(expireObservations.expireDueObservations()).isZero();
		assertThat(stateOf(id)).isEqualTo("OBSERVING");
	}

	/** 이미 넘어간 것을 다시 전이시키면 상태기계가 예외를 던진다. 매 틱 도는 작업이라 멱등이어야 한다. */
	@Test
	void alreadyPendingObservationsAreNotTouchedAgain() {
		long id = purchase(variantId(), Duration.ofDays(400), 90, "REPORT_PENDING");

		assertThat(expireObservations.expireDueObservations()).isZero();
		assertThat(stateOf(id)).isEqualTo("REPORT_PENDING");
	}

	@Test
	void onlyDueObservationsMove() {
		long variantId = variantId();
		long due = purchase(variantId, Duration.ofDays(120), 90, "OBSERVING");
		long running = purchase(variantId, Duration.ofDays(10), 90, "OBSERVING");

		assertThat(expireObservations.expireDueObservations()).isEqualTo(1);
		assertThat(stateOf(due)).isEqualTo("REPORT_PENDING");
		assertThat(stateOf(running)).isEqualTo("OBSERVING");
	}

	/** 관찰 기간은 구매마다 다르다. 90을 상수로 박으면 30일 관찰이 90일까지 살아 있다. */
	@Test
	void shorterObservationExpiresSooner() {
		long id = purchase(variantId(), Duration.ofDays(31), 30, "OBSERVING");

		assertThat(expireObservations.expireDueObservations()).isEqualTo(1);
		assertThat(stateOf(id)).isEqualTo("REPORT_PENDING");
	}

	/**
	 * PUR-02 스냅샷은 <b>구매 시점에 동결</b>된다. 전이가 delete+insert거나 엔티티 전체를 다시 쓰면
	 * 동결값이 조용히 바뀐다 — `alert_policy`에서 배운 것(docs/99).
	 */
	@Test
	void expiringPreservesTheFrozenSnapshot() {
		long id = purchase(variantId(), Duration.ofDays(91), 90, "OBSERVING");

		expireObservations.expireDueObservations();

		Map<String, Object> row = jdbc.queryForMap(
				"select snap_benchmark_price, snap_tier, snap_n, snap_m, paid_price from purchase where id = ?", id);
		assertThat(row).containsEntry("snap_benchmark_price", 820000L)
			.containsEntry("snap_tier", "SUFFICIENT")
			.containsEntry("snap_n", 12)
			.containsEntry("snap_m", 3)
			.containsEntry("paid_price", 899000L);
	}
}
