package dev.hogumeter.core.domain.deal;

import java.time.Instant;

/**
 * DealEvent 테스트 픽스처 빌더 — "가독성이 곧 요구 추적성"(docs/benchmark/06).
 * 결정적 기본값(랜덤 없음). {@code aDealEvent().withPriceFirst(890_000).crossVerified().origin(BACKFILL)}.
 */
public final class DealEventBuilder {

	private long variantId = 1L;
	private long priceFirst = 890_000L;
	private boolean crossVerified = false;
	private Origin origin = Origin.LIVE;
	private OutlierFlag outlierFlag = OutlierFlag.NONE;
	private Instant firstSeen = Instant.parse("2026-07-01T00:00:00Z");
	private String site = "ppomppu";
	private String sourceUrl = "https://ppomppu.test/deal";

	private DealEventBuilder() {
	}

	public static DealEventBuilder aDealEvent() {
		return new DealEventBuilder();
	}

	public DealEventBuilder withVariantId(long v) {
		this.variantId = v;
		return this;
	}

	public DealEventBuilder withPriceFirst(long p) {
		this.priceFirst = p;
		return this;
	}

	public DealEventBuilder crossVerified() {
		this.crossVerified = true;
		return this;
	}

	public DealEventBuilder singleSite() {
		this.crossVerified = false;
		return this;
	}

	public DealEventBuilder origin(Origin o) {
		this.origin = o;
		return this;
	}

	public DealEventBuilder outlier(OutlierFlag f) {
		this.outlierFlag = f;
		return this;
	}

	public DealEventBuilder firstSeen(Instant i) {
		this.firstSeen = i;
		return this;
	}

	public DealEventBuilder firstSeen(String iso) {
		this.firstSeen = Instant.parse(iso);
		return this;
	}

	public DealEventBuilder withSite(String s) {
		this.site = s;
		return this;
	}

	public DealEventBuilder withSourceUrl(String u) {
		this.sourceUrl = u;
		return this;
	}

	public DealEvent build() {
		return new DealEvent(variantId, priceFirst, crossVerified, origin, outlierFlag, firstSeen, site, sourceUrl);
	}
}
