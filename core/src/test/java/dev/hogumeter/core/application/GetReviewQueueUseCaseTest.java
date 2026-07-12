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
		// 단일 재적재 행: last_seen_at = created_at(first=last). Q-27④ 후 읽기 모델이 컬럼을 직접 읽는다.
		return jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status, created_at, last_seen_at)
				values (?, ?::jsonb, ?, ?::timestamptz, ?::timestamptz) returning id
				""", Long.class, type, payload, status, createdAt, createdAt);
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

	/**
	 * "700,000원"만 보고는 아무것도 결정할 수 없다. <b>무엇의</b> 이상치인지 말해야 한다 —
	 * `후보 2개`와 같은 자리다.
	 */
	@Test
	void outlierItemNamesWhatItIsAbout() {
		long productId = jdbc.queryForObject(
				"insert into product (name, category) values ('아이폰 17', 'phone') returning id", Long.class);
		long variantId = jdbc.queryForObject("""
				insert into variant (product_id, label, price_axis_values) values (?, '256GB', '{}'::jsonb)
				returning id
				""", Long.class, productId);
		long dealId = jdbc.queryForObject("""
				insert into deal_event (variant_id, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen)
				values (?, 700000, 700000, 700000, 700000, 'LIVE', 'ACTIVE', now(), now()) returning id
				""", Long.class, variantId);
		long itemId = enqueue("OUTLIER_LOWER",
				"""
				{"priceFirst":700000,"dealEventId":%d}""".formatted(dealId),
				"PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).subject()).isEqualTo("아이폰 17 — 256GB");
	}

	/** 미상 딜(variant 미배정)의 이상치는 대상을 말할 수 없다. "" 대신 null — 화면이 그 사실을 말한다. */
	@Test
	void outlierOnAnUnclassifiedDealHasNoSubject() {
		long dealId = jdbc.queryForObject("""
				insert into deal_event (variant_id, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen)
				values (null, 700000, 700000, 700000, 700000, 'LIVE', 'ACTIVE', now(), now()) returning id
				""", Long.class);
		long itemId = enqueue("OUTLIER_LOWER",
				"""
				{"priceFirst":700000,"dealEventId":%d}""".formatted(dealId),
				"PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).subject()).isNull();
	}

	/** 미상 항목은 정의상 대상이 없다 — 그게 미상이라는 뜻이다. */
	@Test
	void unclassifiedItemHasNoSubject() {
		long itemId = enqueue("UNCLASSIFIED", "{\"title\":\"x\"}", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).subject()).isNull();
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
	 * 이제 쓰기 쪽이 같은 근거를 <b>한 행에 접고</b> 재적재를 {@code occurrences} 컬럼으로 센다
	 * (그 접힘 자체는 {@code IngestDealsUseCaseTest}가 검증한다). 이 조회는 그 컬럼을 그대로 읽어야 한다 —
	 * {@code count(*)}로 재구성하지 않는다(그러면 한 행이 왔을 때 occurrences가 늘 1이 된다).
	 */
	@Test
	void pendingItemReadsOccurrenceColumnAndTimestamps() {
		long id = jdbc.queryForObject("""
				insert into review_queue_item
				       (type, payload, status, created_at, occurrences, last_seen_at, dedup_key)
				values ('UNCLASSIFIED', ?::jsonb, 'PENDING', ?::timestamptz, 3, ?::timestamptz, 'u:rq-fold')
				returning id
				""", Long.class, "{\"title\":\"매 틱 다시 쌓인다\",\"productCandidates\":[]}",
				"2026-07-10T00:00:00Z", "2026-07-10T00:02:00Z");

		List<PendingItem> items = mine(id);

		assertThat(items).hasSize(1);
		assertThat(items.get(0).occurrences()).isEqualTo(3); // 컬럼을 읽는다(재적재 3회)
		assertThat(items.get(0).firstSeenAt()).isEqualTo(Instant.parse("2026-07-10T00:00:00Z")); // created_at
		assertThat(items.get(0).lastSeenAt()).isEqualTo(Instant.parse("2026-07-10T00:02:00Z")); // last_seen_at
	}

	/**
	 * 화면이 "후보 2개"라고만 말하면 사람은 아무 판단도 못 한다. <b>무엇의 후보인지</b>를 말해야 한다.
	 * id는 사람이 읽는 값이 아니다.
	 */
	@Test
	void candidateProductIdsAreResolvedToNames() {
		long productId = jdbc.queryForObject(
				"insert into product (name, category) values ('아이폰 17', 'phone') returning id", Long.class);
		long itemId = enqueue("UNCLASSIFIED",
				"""
				{"title":"정체불명","productCandidates":[%d]}""".formatted(productId),
				"PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).candidateProducts()).containsExactly("아이폰 17");
	}

	/** 후보가 없으면 빈 목록이다. `null`이나 "없음" 같은 문자열을 지어내지 않는다. */
	@Test
	void noCandidatesIsAnEmptyList() {
		long itemId = enqueue("OUTLIER_LOWER", "{\"priceFirst\":700000}", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).candidateProducts()).isEmpty();
	}

	/** 사라진 제품을 조용히 빼면 "후보 2개"가 "후보 1개"가 된다 — 근거가 줄어든 걸 아무도 모른다. */
	@Test
	void aCandidateThatNoLongerExistsIsShownAsItsIdNotDropped() {
		long itemId = enqueue("UNCLASSIFIED", "{\"title\":\"x\",\"productCandidates\":[999999]}",
				"PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).candidateProducts()).containsExactly("#999999");
	}

	/** 근거가 다르면 다른 항목이다. 접기가 서로 다른 딜을 뭉개면 안 된다. */
	@Test
	void differentEvidenceStaysSeparate() {
		long one = enqueue("UNCLASSIFIED", "{\"title\":\"A\"}", "PENDING", "2026-07-10T00:00:00Z");
		long two = enqueue("UNCLASSIFIED", "{\"title\":\"B\"}", "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(one, two)).hasSize(2).allSatisfy(item -> assertThat(item.occurrences()).isEqualTo(1));
	}

	/**
	 * 이상치는 <b>왜</b> 싸 보이는지 말해야 한다. `700,000원`만 보고는 아무것도 결정할 수 없다 —
	 * 그 가격이 `카할`(특정 카드 보유자만)이거나 `배송비미상`(하한)이면 이상치가 아니라 **정상**이다.
	 *
	 * <p>조건 태그는 `PreserveAppliedConditionsUseCase`가 원문에서 딜로 끌어올린다(BM-02 AC-2).
	 * 여기서 보여 주지 않으면 그 값은 파이프라인 로그에만 있고, 판단하는 사람은 원문을 처음부터 읽는다.
	 */
	@Test
	void outlierItemShowsWhyItLooksCheap() {
		long postId = rawPost("rq-cond", "https://example.invalid/rq-cond", "너무 싼 딜");
		long dealId = jdbc.queryForObject("""
				insert into deal_event (variant_id, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen, applied_conditions)
				values (null, 700000, 700000, 700000, 700000, 'LIVE', 'ACTIVE', now(), now(),
				        array['카할','배송비미상']) returning id
				""", Long.class);
		jdbc.update("insert into deal_event_source (deal_event_id, raw_deal_post_id, site) values (?, ?, 'ppomppu')",
				dealId, postId);
		long itemId = enqueue("OUTLIER_LOWER",
				"""
				{"priceFirst":700000,"dealEventId":%d}""".formatted(dealId), "PENDING", "2026-07-10T00:00:00Z");

		PendingItem item = mine(itemId).get(0);

		assertThat(item.conditions()).containsExactly("배송비미상", "카할"); // 정렬 고정(collate "C")
	}

	/** 조건 없는 이상치는 빈 목록이다. "값 없음"을 `null`로 흘리지 않는다. */
	@Test
	void anOutlierWithoutConditionsHasAnEmptyList() {
		long postId = rawPost("rq-nocond", "https://example.invalid/rq-nocond", "그냥 싼 딜");
		long dealId = jdbc.queryForObject("""
				insert into deal_event (variant_id, price_first, price_min, price_max, price_last,
				                        origin, status, first_seen, last_seen)
				values (null, 700000, 700000, 700000, 700000, 'LIVE', 'ACTIVE', now(), now()) returning id
				""", Long.class);
		jdbc.update("insert into deal_event_source (deal_event_id, raw_deal_post_id, site) values (?, ?, 'ppomppu')",
				dealId, postId);
		long itemId = enqueue("OUTLIER_LOWER",
				"""
				{"priceFirst":700000,"dealEventId":%d}""".formatted(dealId), "PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).conditions()).isEmpty();
	}

	/** 미상(UNCLASSIFIED) 항목엔 딜이 없다 — 조건도 없다. */
	@Test
	void unclassifiedItemsHaveNoConditions() {
		long postId = rawPost("rq-u", "https://example.invalid/rq-u", "정체불명");
		long itemId = enqueue("UNCLASSIFIED",
				"""
				{"title":"정체불명","rawDealPostId":%d,"productCandidates":[]}""".formatted(postId),
				"PENDING", "2026-07-10T00:00:00Z");

		assertThat(mine(itemId).get(0).conditions()).isEmpty();
	}
}
