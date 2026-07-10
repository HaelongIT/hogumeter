package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DealTags#SHIPPING_UNKNOWN}은 collector가 만든 값의 <b>사본</b>이다. 사본이 드리프트하면 core는
 * 조용히 0을 세면서 "배송비 미상 딜 없음"이라고 말한다 — GREEN인 채로 거짓말한다.
 *
 * <p>두 방향으로 잠근다: ① {@code scripts/check-tag-contract.sh}가 두 리터럴이 같은지 CI에서 본다
 * ② 이 테스트가 <b>그 리터럴로 실제 DB 배열을 찾을 수 있는지</b> 본다. 상수가 옳아도 질의가 못 찾으면
 * 카운터는 여전히 0이다(인코딩·정규화가 어긋나면 그렇게 된다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DealTagsContractTest {

	@Autowired
	JdbcTemplate jdbc;

	private long dealWithConditions(String... conditions) {
		return jdbc.queryForObject("""
				insert into deal_event (price_first, price_min, price_max, price_last, origin,
				                        first_seen, last_seen, applied_conditions)
				values (1000, 1000, 1000, 1000, 'LIVE', now(), now(), ?) returning id
				""", Long.class, (Object) conditions);
	}

	private long countShippingUnknown() {
		return jdbc.queryForObject(
				"select count(*) from deal_event where ? = any(applied_conditions) and id = any(?)",
				Long.class, DealTags.SHIPPING_UNKNOWN, new Long[] { mine[0], mine[1] });
	}

	private Long[] mine = new Long[2];

	@Test
	void theMarkerFindsTaggedDealsAndOnlyThose() {
		mine[0] = dealWithConditions("유료배송(금액미상)", DealTags.SHIPPING_UNKNOWN);
		mine[1] = dealWithConditions("카할"); // as-posted로 옳은 값 — 표식 없음

		assertThat(countShippingUnknown()).isEqualTo(1);
	}

	/** 표식이 배열의 어느 자리에 있든 찾는다(`= any(...)`는 위치를 묻지 않는다). */
	@Test
	void theMarkerIsFoundRegardlessOfPositionInTheArray() {
		mine[0] = dealWithConditions(DealTags.SHIPPING_UNKNOWN, "조건부무료배송:와우무배");
		mine[1] = dealWithConditions("조건부무료배송:네멤무료", DealTags.SHIPPING_UNKNOWN);

		assertThat(countShippingUnknown()).isEqualTo(2);
	}
}
