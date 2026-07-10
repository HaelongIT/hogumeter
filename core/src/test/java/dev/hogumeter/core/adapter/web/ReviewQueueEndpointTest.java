package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 미상 큐 REST(읽기 전용). 승격·기각은 없다 — `DealEventEntity`에 전이 메서드가, `ReviewQueueItemEntity`에
 * `status` 매핑이 없어 "처리됨"을 쓸 수 없다(상대 소유 파일, docs/91 Q-15).
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
}
