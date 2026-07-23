package dev.hogumeter.core.application;

import java.time.Instant;

/** CMP-01 재료 — variant의 최신 쿠팡 관측(read-model). null 필드는 "값 없음"이지 0이 아니다. */
public record CoupangPriceView(long regularPrice, Long wowPrice, Long shippingFee, String url,
		Instant observedAt) {
}
