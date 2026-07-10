package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * BM-02 AC-2 — <b>조건부 가격의 태그 보존</b>.
 *
 * <p>확정본: "890,000이 그대로 분포 입력이 되고, applied_conditions에 태그(카드명)만 남는다. base_price
 * 역산 시도는 없다." 분포는 as-posted가 맞다. <b>그런데 태그가 남지 않았다.</b>
 *
 * <p>collector는 조건 태그를 뽑아 {@code raw_deal_post.raw._derived.applied_conditions}에 싣는다(골든 실측:
 * 뽐뿌 9.5% · 펨코 15%). 그런데 {@code deal_event.applied_conditions} 컬럼은 V1에 있으면서도
 * <b>아무도 쓰지 않았다</b> — 항상 NULL이다. 즉 "N카드 할인 시 890,000"이 무조건 890,000처럼 보인다.
 * 화면·알림·로그 어디에도 표시가 없다(절대 원칙 1 정직성, 6 과대약속 금지 위반).
 *
 * <p>{@code IngestDealsUseCase}·{@code DealEventEntity}는 상대 개발자 소유라 고치지 않는다. 딜은 이미
 * 만들어져 있고 원문 링크({@code deal_event_source})가 있으므로, <b>신규 파일 + 네이티브 SQL</b>로
 * 태그를 원문에서 딜로 끌어올린다. 엔티티가 매핑하지 않는 컬럼을 JPA로 건드리지 않는 것과 같은 이유다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PreserveAppliedConditionsUseCaseTest {

	@Autowired
	PreserveAppliedConditionsUseCase preserve;
	@Autowired
	JdbcTemplate jdbc;

	private long rawPost(String postId, String rawJson) {
		return jdbc.queryForObject("""
				insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status, raw)
				values ('ppomppu', ?, 'https://example.invalid/' || ?, '조건부 특가', 890000, now(), 'ACTIVE', ?::jsonb)
				returning id
				""", Long.class, postId, postId, rawJson);
	}

	private long dealEvent() {
		return jdbc.queryForObject("""
				insert into deal_event (price_first, price_min, price_max, price_last, origin, first_seen, last_seen)
				values (890000, 890000, 890000, 890000, 'LIVE', now(), now()) returning id
				""", Long.class);
	}

	private void link(long dealId, long postId) {
		jdbc.update("""
				insert into deal_event_source (deal_event_id, raw_deal_post_id, site)
				values (?, ?, 'ppomppu')""", dealId, postId);
	}

	private List<String> conditionsOf(long dealId) {
		java.sql.Array array = jdbc.queryForObject(
				"select applied_conditions from deal_event where id = ?", java.sql.Array.class, dealId);
		try {
			return array == null ? null : List.of((String[]) array.getArray());
		}
		catch (java.sql.SQLException failure) {
			throw new IllegalStateException(failure);
		}
	}

	/** 원문의 태그가 딜에 도달한다. 이게 없어서 표본의 약 1할이 무조건 가격 행세를 했다. */
	@Test
	void tagsTravelFromTheOriginalPostToTheDeal() {
		long post = rawPost("cond-1", """
				{"category":"디지털","_derived":{"applied_conditions":["카할"]}}""");
		long deal = dealEvent();
		link(deal, post);

		int touched = preserve.preserveTags();

		assertThat(touched).isGreaterThanOrEqualTo(1);
		assertThat(conditionsOf(deal)).containsExactly("카할");
	}

	/** 병합된 딜(원문 여러 개)의 태그는 합집합이다. 한 곳에서만 조건부여도 그 가격은 조건부다. */
	@Test
	void mergedDealUnionsTagsFromEverySource() {
		long first = rawPost("cond-2a", """
				{"_derived":{"applied_conditions":["카할"]}}""");
		long second = rawPost("cond-2b", """
				{"_derived":{"applied_conditions":["조건부무료배송:와우무배","카할"]}}""");
		long deal = dealEvent();
		link(deal, first);
		link(deal, second);

		preserve.preserveTags();

		// 정렬 고정 + 중복 제거 — 같은 태그가 두 원문에 있어도 한 번만.
		assertThat(conditionsOf(deal)).containsExactly("조건부무료배송:와우무배", "카할");
	}

	/** 조건 없는 딜은 건드리지 않는다. NULL과 빈 배열은 다르다 — "태그가 없다"를 "빈 배열"로 쓰지 않는다. */
	@Test
	void dealsWithoutTagsAreLeftAlone() {
		long post = rawPost("cond-3", """
				{"category":"디지털"}""");
		long deal = dealEvent();
		link(deal, post);

		preserve.preserveTags();

		assertThat(conditionsOf(deal)).isNull();
	}

	/** 매 틱 도는 단계다. 두 번째 실행은 아무것도 바꾸지 않는다(0을 반환) — 아니면 카운터가 영원히 거짓말한다. */
	@Test
	void secondRunChangesNothing() {
		long post = rawPost("cond-4", """
				{"_derived":{"applied_conditions":["카할"]}}""");
		long deal = dealEvent();
		link(deal, post);

		preserve.preserveTags();
		int second = preserve.preserveTags();

		assertThat(second).isZero();
		assertThat(conditionsOf(deal)).containsExactly("카할");
	}

	/** 사람이 손으로 고쳤거나 상대가 ingest에서 채우기 시작하면, 우리가 덮어쓰지 않는다 — 값이 같으면 무동작. */
	@Test
	void alreadyCorrectTagsAreNotRewritten() {
		long post = rawPost("cond-5", """
				{"_derived":{"applied_conditions":["카할"]}}""");
		long deal = dealEvent();
		link(deal, post);
		jdbc.update("update deal_event set applied_conditions = array['카할'] where id = ?", deal);

		assertThat(preserve.preserveTags()).isZero();
	}
}
