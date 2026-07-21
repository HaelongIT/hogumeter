package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * PUR-04 성적표 발급. <b>이 유스케이스가 없던 동안 구매는 REPORT_PENDING에서 영원히 멈췄다</b> —
 * {@code ReportCardCalculator}는 순수 도메인에 있고 테스트도 GREEN이었지만 프로덕션 호출자가 0이었다.
 * 여기서 발급이 실제로 성적표 행을 만들고 CLOSED로 닫는지, 멱등한지, REPORT_PENDING만 대상인지 관통으로 잠근다.
 *
 * <p>@Transactional로 tx 롤백. 컨테이너 공유라 삽입한 id로 스코프한다(docs/99).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IssuePendingReportCardsUseCaseTest {

	@Autowired
	IssuePendingReportCardsUseCase issueReportCards;
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

	/** 창 안/밖 딜을 firstSeen 시각으로 심는다. priceFirst=percentile 입력, priceMin=최저 기회. */
	private void deal(long variantId, Duration firstSeenAgo, long priceFirst, long priceMin) {
		Instant t = Instant.now().minus(firstSeenAgo);
		jdbc.update("""
				insert into deal_event (variant_id, unclassified, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen)
				values (?, false, ?, ?, ?, ?, 'LIVE', 'ACTIVE', ?::timestamptz, ?::timestamptz)
				""", variantId, priceFirst, priceMin, priceFirst, priceFirst, t.toString(), t.toString());
	}

	/** purchasedAt을 과거로 밀어 관찰창을 만든다. snap_benchmark_price=동결 기준가(paidGap 근거). */
	private long purchase(long variantId, Duration purchasedAgo, int observationDays, String state, Long snapBenchmark) {
		return jdbc.queryForObject("""
				insert into purchase (variant_id, paid_price, purchased_at, observation_days, state,
				                      snap_benchmark_price, snap_tier, snap_n, snap_m, snap_unobserved)
				values (?, 899000, ?::timestamptz, ?, ?, ?, 'SUFFICIENT', 12, 3, false) returning id
				""", Long.class, variantId, Instant.now().minus(purchasedAgo).toString(), observationDays, state,
				snapBenchmark);
	}

	private String stateOf(long purchaseId) {
		return jdbc.queryForObject("select state from purchase where id = ?", String.class, purchaseId);
	}

	@Test
	void issuesReportCardAtReportPendingAndClosesThePurchase() {
		long v = variantId();
		// 관찰창 [100일전, 10일전]. 110일전 딜은 창 밖이지만 observedFrom을 구매 이전으로 당겨 UNOBSERVED를 막는다.
		deal(v, Duration.ofDays(110), 900_000, 900_000); // 창 밖(관측 시작만 표시)
		deal(v, Duration.ofDays(50), 850_000, 840_000); // 창 안 · 싼 딜
		deal(v, Duration.ofDays(30), 880_000, 870_000); // 창 안 · 싼 딜
		deal(v, Duration.ofDays(20), 950_000, 950_000); // 창 안 · 안 싼 딜
		em.flush();
		long p = purchase(v, Duration.ofDays(100), 90, "REPORT_PENDING", 820_000L);
		em.flush();

		int issued = issueReportCards.issuePendingReportCards();

		assertThat(issued).isEqualTo(1);
		assertThat(stateOf(p)).isEqualTo("CLOSED");
		Map<String, Object> card = jdbc.queryForMap("select * from report_card where purchase_id = ?", p);
		assertThat(card.get("unobserved")).isEqualTo(false);
		assertThat(((Number) card.get("n")).intValue()).isEqualTo(3); // 창 안 셋(110일전 제외)
		assertThat(((Number) card.get("cheaper_count")).intValue()).isEqualTo(2); // 850k·880k < 899k
		assertThat(((Number) card.get("paid_price")).longValue()).isEqualTo(899_000L);
		assertThat(((Number) card.get("paid_gap")).longValue()).isEqualTo(79_000L); // 899k − 동결 820k
		assertThat(((Number) card.get("lowest_opportunity")).longValue()).isEqualTo(840_000L); // min priceMin(창 안)
		assertThat(new BigDecimal(card.get("percentile").toString())).isEqualByComparingTo("0.667"); // 2/3
	}

	@Test
	void isIdempotent_secondRunIssuesNothingAndKeepsOneCard() {
		long v = variantId();
		deal(v, Duration.ofDays(110), 900_000, 900_000);
		deal(v, Duration.ofDays(50), 850_000, 840_000);
		em.flush();
		long p = purchase(v, Duration.ofDays(100), 90, "REPORT_PENDING", 820_000L);
		em.flush();

		assertThat(issueReportCards.issuePendingReportCards()).isEqualTo(1);
		assertThat(issueReportCards.issuePendingReportCards()).isEqualTo(0); // 재발급 없음(CLOSED라 대상 밖 + 유니크)
		Integer cards = jdbc.queryForObject("select count(*) from report_card where purchase_id = ?", Integer.class, p);
		assertThat(cards).isEqualTo(1);
		assertThat(stateOf(p)).isEqualTo("CLOSED");
	}

	@Test
	void observingPurchaseIsNotIssued() {
		long v = variantId();
		deal(v, Duration.ofDays(50), 850_000, 840_000);
		em.flush();
		long p = purchase(v, Duration.ofDays(10), 90, "OBSERVING", 820_000L); // 관찰 중(만료 전)
		em.flush();

		assertThat(issueReportCards.issuePendingReportCards()).isEqualTo(0);
		assertThat(stateOf(p)).isEqualTo("OBSERVING");
		Integer cards = jdbc.queryForObject("select count(*) from report_card where purchase_id = ?", Integer.class, p);
		assertThat(cards).isEqualTo(0);
	}
}
