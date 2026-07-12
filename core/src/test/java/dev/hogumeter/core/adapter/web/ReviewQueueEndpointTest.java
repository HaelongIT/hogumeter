package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미상 큐 REST. 조회 + 승격·기각(Q-15). 여기선 HTTP 계약(상태코드·에러코드)을 시험한다 — 도메인 효과
 * (승격이 이상치 플래그를 지우는 것 등)는 {@code ResolveReviewItemUseCaseTest}가 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewQueueEndpointTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void exposesPendingItemsWithTheirEvidence() throws Exception {
		Long postId = jdbc.queryForObject("""
				insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
				values ('ppomppu', 'rq-endpoint', 'https://example.invalid/e1', '미상 딜', 999000, now(), 'ACTIVE')
				returning id
				""", Long.class);
		Long itemId = jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status)
				values ('UNCLASSIFIED', ?::jsonb, 'PENDING') returning id
				""", Long.class, """
				{"title":"미상 딜","rawDealPostId":%d,"productCandidates":[]}""".formatted(postId));

		mockMvc.perform(get("/api/v1/review-queue"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == %d)].type".formatted(itemId)).value("UNCLASSIFIED"))
			.andExpect(jsonPath("$[?(@.id == %d)].sourceUrl".formatted(itemId))
					.value("https://example.invalid/e1"))
			.andExpect(jsonPath("$[?(@.id == %d)].payload.title".formatted(itemId)).value("미상 딜"));
	}

	/** 큐가 비면 빈 배열이다 — 404가 아니다. "볼 게 없다"는 정상 상태다. */
	@Test
	void emptyQueueIsAnArrayNotAnError() throws Exception {
		mockMvc.perform(get("/api/v1/review-queue"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	@Test
	void rejectingAnUnclassifiedItemReturnsOkAndTakesItOffTheQueue() throws Exception {
		Long itemId = jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status)
				values ('UNCLASSIFIED', '{"title":"정체불명","productCandidates":[]}'::jsonb, 'PENDING') returning id
				""", Long.class);

		mockMvc.perform(post("/api/v1/review-queue/%d/reject".formatted(itemId)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/review-queue"))
			.andExpect(jsonPath("$[?(@.id == %d)]".formatted(itemId)).isEmpty()); // 큐에서 내려갔다
	}

	@Test
	void promotingAnUnclassifiedItemIsRejectedWithCode() throws Exception {
		Long itemId = jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status)
				values ('UNCLASSIFIED', '{"title":"정체불명","productCandidates":[]}'::jsonb, 'PENDING') returning id
				""", Long.class);

		mockMvc.perform(post("/api/v1/review-queue/%d/promote".formatted(itemId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("REVIEW_PROMOTE_UNSUPPORTED"));
	}

	@Test
	void resolvingAMissingItemReturnsNotFoundWithCode() throws Exception {
		mockMvc.perform(post("/api/v1/review-queue/999999/reject"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REVIEW_ITEM_NOT_FOUND"));
	}
}
