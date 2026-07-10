package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실 DataSource로 프로브가 돈다는 것을 증명한다. 단위 테스트는 프로브를 주입하므로 <b>진짜 커넥션이
 * 잡히는지</b>를 보지 못하고, compose healthcheck는 이 경로가 200을 줄 것을 전제로 스택 전체를 세운다.
 *
 * <p>경로(`/api/v1/health`)를 여기서 못박는다 — 경로가 바뀌면 compose는 조용히 unhealthy가 된다(Q-50 ③).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void reportsUpWithARealDatabaseConnection() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.db.status").value("UP"));
	}

	/** 건강할 때 `"error": null`을 싣지 않는다 — 없는 오류를 있는 것처럼 그리는 클라이언트가 나온다. */
	@Test
	void omitsTheErrorFieldWhenHealthy() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("error"))));
	}
}
