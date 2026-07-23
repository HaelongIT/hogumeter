package dev.hogumeter.core;

import dev.hogumeter.core.application.AlertDispatcher;
import dev.hogumeter.core.application.VariantNaming;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.alert.AlertEvaluator;
import dev.hogumeter.core.domain.alert.AlertGate;
import dev.hogumeter.core.domain.used.ListingExtractor;
import dev.hogumeter.core.domain.used.RuleBasedListingExtractor;
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

	/** 순수 도메인(evaluator/gate)은 Spring 비의존이라 여기서 조립. 발송은 주입된 out-port(스텁). */
	@Bean
	AlertDispatcher alertDispatcher(AlertSender alertSender, VariantNaming variantNaming) {
		return new AlertDispatcher(new AlertEvaluator(), new AlertGate(), alertSender, variantNaming::of);
	}

	/** USED-04 AC-15: v1은 규칙 기반. 인터페이스 뒤에 둬 v2(LLM)로 교체할 때 이 한 줄만 바뀐다. */
	@Bean
	ListingExtractor listingExtractor() {
		return new RuleBasedListingExtractor();
	}

}
