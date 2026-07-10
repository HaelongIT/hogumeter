package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미상 큐 읽기. <b>이 조회가 생기기 전까지 `review_queue_item`은 쓰이기만 하고 아무도 읽지 않았다</b> —
 * `IngestDealsUseCase`가 매칭 실패 딜을 넣고 `PipelineScheduler`가 세기만 했다. 즉 매칭이 무엇을
 * 놓치는지 사람이 볼 방법이 없었다(절대 원칙 3: 놓침 > 오알림 — 그런데 놓친 걸 볼 수가 없었다).
 *
 * <p>`status`·`created_at`은 상대 소유 `ReviewQueueItemEntity`가 매핑하지 않는다. 그래서 JPA가 아니라
 * 읽기 전용 SQL로 읽는다 — 엔티티를 고치지 않고 진실을 본다.
 *
 * <p>@Transactional로 tx 롤백. 컨테이너는 공유되므로 <b>전역 단정 대신 삽입한 id로 스코프</b>한다(docs/99).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetReviewQueueUseCaseTest {

	@Autowired
	GetReviewQueueUseCase reviewQueue;
	@Autowired
	JdbcTemplate jdbc;

	private long rawPost(String postId, String url, String title) {
		return jdbc.queryForObject("""
				insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
				values ('ppomppu', ?, ?, ?, 999000, now(), 'ACTIVE') returning id
				""", Long.class, postId, url, title);
	}

	private long enqueue(String type, String payload, String status, String createdAt) {
		return jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status, created_at)
				values (?, ?::jsonb, ?, ?::timestamptz) returning id
				""", Long.class, type, payload, status, createdAt);
	}

	private List<PendingItem> mine(long... ids) {
		List<Long> wanted = java.util.Arrays.stream(ids).boxed().toList();
		return reviewQueue.pending().stream().filter(item -> wanted.contains(item.id())).toList();
	}

	@Test
	void unclassifiedItemCarriesTheEvidenceAndLinksToTheOriginalPost() {
		long postId = rawPost("rq-1", "https://example.invalid/rq-1", "정체불명 특가");
		long itemId = enqueue("UNCLASSIFIED",
				"""
				{"title":"정체불명 특가","rawDealPostId":%d,"productCandidates":[7]}""".formatted(postId),
				"PENDING", "2026-07-10T00:00:00Z");

		PendingItem item = mine(itemId).get(0);

		assertThat(item.type()).isEqualTo(ReviewQueueType.UNCLASSIFIED);
		// 판단은 사람이 한다 — 시스템은 근거와 원문 링크만 모아준다(절대 원칙 2·6).
		assertThat(item.sourceUrl()).isEqualTo("https://example.invalid/rq-1");
		assertThat(item.payload()).containsEntry("title", "정체불명 특가");
		assertThat(item.payload().get("productCandidates")).isEqualTo(List.of(7));
	}

	/** `deal_event`에는 url 컬럼이 없다 — `deal_event_source`를 거쳐 원문에 닿는다. */
	@Test
	void outlierItemLinksThroughItsSource() {
		long postId = rawPost("rq-2", "https://example.invalid/rq-2", "너무 싼 딜");
		long dealId = jdbc.queryForObject("""
				insert into deal_event (variant_id, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen)
				values (null, 700000, 700000, 700000, 700000, 'LIVE', 'ACTIVE', now(), now()) returning id
				""", Long.class);
		jdbc.update("insert into deal_event_source (deal_event_id, raw_deal_post_id, site) values (?, ?, 'ppomppu')",
				dealId, postId);
		long itemId = enqueue("OUTLIER_LOWER",
				"""
				{"priceFirst":700000,"dealEventId":%d}""".formatted(dealId),
				"PENDING", "2026-07-10T00:00:00Z");

		PendingItem item = mine(itemId).get(0);

		assertThat(item.sourceUrl()).isEqualTo("https://example.invalid/rq-2");
		assertThat(item.payload()).containsEntry("priceFirst", 700000);
	}

	/** 원문을 잇지 못하면 `null`이다. 빈 문자열이나 "#"으로 채우면 화면이 죽은 링크를 그린다. */
	@Test
	void anItemWhoseOriginCannotBeResolvedHasNoUrlRatherThanAFakeOne() {
		long itemId = enqueue("KEYWORD_SUGGEST", """
				{"tokens":["리퍼"]}""", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).sourceUrl()).isNull();
	}

	/** 처리된 항목은 큐가 아니다. `status`는 엔티티가 매핑하지 않아 JPA로는 이 구분을 할 수 없다. */
	@Test
	void resolvedItemsAreNotInTheQueue() {
		long pending = enqueue("UNCLASSIFIED", "{\"title\":\"남은 것\"}", "PENDING", "2026-07-10T00:00:00Z");
		long confirmed = enqueue("UNCLASSIFIED", "{\"title\":\"처리됨\"}", "CONFIRMED", "2026-07-10T00:00:00Z");
		long rejected = enqueue("UNCLASSIFIED", "{\"title\":\"기각됨\"}", "REJECTED", "2026-07-10T00:00:00Z");

		assertThat(mine(pending, confirmed, rejected)).extracting(PendingItem::id).containsExactly(pending);
	}

	/** 최신이 위. 큐가 길어지면 사람은 위에서부터 본다. */
	@Test
	void newestFirst() {
		long older = enqueue("UNCLASSIFIED", "{\"title\":\"어제\"}", "PENDING", "2026-07-09T00:00:00Z");
		long newer = enqueue("UNCLASSIFIED", "{\"title\":\"오늘\"}", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(older, newer)).extracting(PendingItem::id).containsExactly(newer, older);
	}

	@Test
	void timesAreExposedBecauseTheEntityHidesThem() {
		long itemId = enqueue("UNCLASSIFIED", "{\"title\":\"시각\"}", "PENDING", "2026-07-10T01:23:00Z");

		PendingItem item = mine(itemId).get(0);
		assertThat(item.firstSeenAt()).isEqualTo(Instant.parse("2026-07-10T01:23:00Z"));
		assertThat(item.lastSeenAt()).isEqualTo(Instant.parse("2026-07-10T01:23:00Z"));
		assertThat(item.occurrences()).isEqualTo(1);
	}

	/**
	 * <b>매칭 실패 원문은 매 틱마다 다시 큐에 쌓인다</b> — `findUnprocessed()`가 `deal_event_source`
	 * 링크 유무로 미처리를 판정하는데, 매칭 실패 딜은 링크를 만들지 않기 때문이다(Q-27 ④).
	 * 조용히 지우면 결함이 사라진 것처럼 보인다. 접어서 <b>세어 보여준다</b>.
	 */
	@Test
	void repeatedEnqueuesOfTheSameEvidenceAreFoldedAndCounted() {
		String payload = "{\"title\":\"매 틱 다시 쌓인다\",\"productCandidates\":[]}";
		long first = enqueue("UNCLASSIFIED", payload, "PENDING", "2026-07-10T00:00:00Z");
		long second = enqueue("UNCLASSIFIED", payload, "PENDING", "2026-07-10T00:01:00Z");
		long third = enqueue("UNCLASSIFIED", payload, "PENDING", "2026-07-10T00:02:00Z");

		List<PendingItem> folded = mine(first, second, third);

		assertThat(folded).hasSize(1);
		assertThat(folded.get(0).id()).isEqualTo(first); // 접힌 행 중 가장 이른 것
		assertThat(folded.get(0).occurrences()).isEqualTo(3);
		assertThat(folded.get(0).firstSeenAt()).isEqualTo(Instant.parse("2026-07-10T00:00:00Z"));
		assertThat(folded.get(0).lastSeenAt()).isEqualTo(Instant.parse("2026-07-10T00:02:00Z"));
	}

	/** 근거가 다르면 다른 항목이다. 접기가 서로 다른 딜을 뭉개면 안 된다. */
	@Test
	void differentEvidenceStaysSeparate() {
		long one = enqueue("UNCLASSIFIED", "{\"title\":\"A\"}", "PENDING", "2026-07-10T00:00:00Z");
		long two = enqueue("UNCLASSIFIED", "{\"title\":\"B\"}", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(one, two)).hasSize(2).allSatisfy(item -> assertThat(item.occurrences()).isEqualTo(1));
	}
}
