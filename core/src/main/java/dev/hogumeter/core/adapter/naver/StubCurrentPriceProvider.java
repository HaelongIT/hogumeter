package dev.hogumeter.core.adapter.naver;

import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import org.springframework.stereotype.Component;

/**
 * 현재가 스텁 — 네이버 쇼핑 API 키 미발급이라 현재가 미확립(0)을 반환한다(Q-3·Q-20).
 * 기준가 산식(tier·median·P25·n·m)은 현재가와 무관하므로 정상 동작하며, 갭만 미확립.
 * 키 발급 후 실 네이버 어댑터로 교체(pre-deploy-checklist §B).
 */
@Component
public class StubCurrentPriceProvider implements CurrentPriceProvider {

	@Override
	public long currentPriceFor(long variantId) {
		return 0L;
	}
}
