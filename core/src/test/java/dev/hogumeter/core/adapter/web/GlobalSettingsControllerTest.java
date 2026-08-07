package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-24(코드리뷰 20260806) — {@code GlobalSettingsController}는 서비스 계층(GlobalExcludeKeywordsTest)만
 * 검증됐고, {@code GET/PUT /api/v1/settings/exclude-keywords}의 경로·JSON 바인딩·상태코드를 관통하는
 * core 쪽 HTTP 레벨 테스트가 0건이었다(web은 {@code SettingsPage.test.tsx}가 API 모듈을 모킹해 화면
 * 동작만 검증한다 — 컨트롤러 배선 자체는 아무도 안 본다).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalSettingsControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void getReturnsEmptyListByDefault() throws Exception {
		mockMvc.perform(get("/api/v1/settings/exclude-keywords"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludeKeywords").isArray())
				.andExpect(jsonPath("$.excludeKeywords").isEmpty());
	}

	@Test
	void putNormalizesAndRoundTripsThroughGet() throws Exception {
		mockMvc.perform(put("/api/v1/settings/exclude-keywords").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"excludeKeywords": [" 리퍼 ", "중고", "  ", "리퍼"]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludeKeywords[0]").value("리퍼"))
				.andExpect(jsonPath("$.excludeKeywords[1]").value("중고"))
				.andExpect(jsonPath("$.excludeKeywords.length()").value(2)); // 공백 다듬기·빈 값 탈락·중복 접기

		mockMvc.perform(get("/api/v1/settings/exclude-keywords"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludeKeywords[0]").value("리퍼"))
				.andExpect(jsonPath("$.excludeKeywords[1]").value("중고"));
	}

	@Test
	void puttingAnEmptyArrayClearsThePreviousList() throws Exception {
		mockMvc.perform(put("/api/v1/settings/exclude-keywords").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"excludeKeywords": ["리퍼"]}"""))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/settings/exclude-keywords").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"excludeKeywords": []}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.excludeKeywords").isEmpty());

		mockMvc.perform(get("/api/v1/settings/exclude-keywords"))
				.andExpect(jsonPath("$.excludeKeywords").isEmpty()); // 전체 교체 — 이전 값이 안 남는다
	}
}
