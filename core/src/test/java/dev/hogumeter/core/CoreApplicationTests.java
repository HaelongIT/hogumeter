package dev.hogumeter.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CoreApplicationTests {

	@Test
	void contextLoads() {
	}

	/**
	 * OPS-03("저장 UTC, 표시 KST. quiet hours는 KST 기준")이 어느 보드에도 없이 몇 주째 코드로
	 * 지켜지지 않고 있었다(docs/91 신규 Q, 2026-08-06 발견) — {@code Clock.systemDefaultZone()}는
	 * JVM 기본 타임존을 따르는데, core Dockerfile(`eclipse-temurin:21-jre`)엔 `TZ` 지정이 없어
	 * 컨테이너 기본값(대개 UTC)으로 뜬다. 이 Clock을 {@link dev.hogumeter.core.domain.alert.AlertGate}가
	 * 그대로 받아 방해금지 시(quiet hours)를 판정하므로, 사용자가 KST로 22~08시를 설정해도 실제로는
	 * UTC 22~08시(KST 07~17시)에 침묵하는 정반대 결과였을 것이다. `BenchmarkCalculator`·`CadenceCalculator`
	 * 등 월/일 경계를 계산하는 다른 순수 도메인도 같은 Clock을 쓴다 — 하나를 고치면 전부 같이 고쳐진다.
	 */
	@Test
	void clockUsesKoreaStandardTimeNotTheContainerDefault() {
		assertThat(new CoreApplication().clock().getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

}
