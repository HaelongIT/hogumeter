package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.hogumeter.core.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기본(텔레그램 미설정)이면 알림은 스텁이라 <b>실제로 안 나간다</b>. REST가 그 사실을 내야 화면이
 * "목표가만 설정하면 알림이 온다"고 과대약속하지 않는다(절대 원칙 6). 실전송(delivering:true)은
 * {@code TelegramSenderWiringTest}가 opt-in 경로로, {@code TelegramAlertSenderTest}가 단위로 잠근다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AlertStatusControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void defaultStubReportsNotDelivering() throws Exception {
		mockMvc.perform(get("/api/v1/alerts/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.delivering").value(false));
	}
}
