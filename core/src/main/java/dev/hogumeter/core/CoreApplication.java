package dev.hogumeter.core;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(CoreApplication.class, args);
	}

	/** 시각 주입 seam — 도메인은 이 Clock을 받아 윈도우·자동확장·firstSeen을 판정한다(Instant.now() 직접 호출 금지). */
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
