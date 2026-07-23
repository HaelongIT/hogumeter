package dev.hogumeter.core.application;

import java.time.Instant;

/**
 * CMP-02 확장이 보내는 관측 1건. {@code wowPrice}·{@code shippingFee}는 null 가능 —
 * "확장이 못 읽었다"를 0으로 흘리지 않는다.
 */
public record CoupangObservationCommand(long variantId, long regularPrice, Long wowPrice, Long shippingFee,
		String url, Instant observedAt) {
}
