package dev.hogumeter.core.domain.deal;

import java.time.Instant;
import java.util.Set;

/**
 * DealEvent 테스트 픽스처 빌더 — "가독성이 곧 요구 추적성"(docs/benchmark/06). 결정적 기본값(랜덤 없음).
 * {@code aDealEvent().withPriceFirst(890_000).crossVerified().origin(BACKFILL)}.
 * crossVerified()는 서로 다른 2사이트를, singleSite()는 1사이트를 세팅한다(sourceSites 파생).
 */
public final class DealEventBuilder {

	private Long variantId = 1L;
	private boolean unclassified = false;
	private Set<Long> productCandidates = Set.of();
	private long priceFirst = 890_000L;
	private Long priceMin = null;
	private Long priceMax = null;
	private Long priceLast = null;
	private Origin origin = Origin.LIVE;
	private Set<String> sourceSites = null;
	private boolean crossFlag = false;
	private OutlierFlag outlierFlag = OutlierFlag.NONE;
	private boolean permanentlyExcluded = false;
	private DealStatus status = DealStatus.ACTIVE;
	private Instant firstSeen = Instant.parse("2026-07-01T00:00:00Z");
	private Instant lastSeen = null;
	private String site = "ppomppu";
	private String sourceUrl = "https://ppomppu.test/deal";
	private Set<String> appliedConditions = Set.of();

	private DealEventBuilder() {
	}

	public DealEventBuilder appliedConditions(String... conditions) {
		this.appliedConditions = Set.of(conditions);
		return this;
	}

	public static DealEventBuilder aDealEvent() {
		return new DealEventBuilder();
	}

	public DealEventBuilder withVariantId(Long v) {
		this.variantId = v;
		return this;
	}

	public DealEventBuilder unclassified(Set<Long> candidates) {
		this.unclassified = true;
		this.variantId = null;
		this.productCandidates = candidates;
		return this;
	}

	public DealEventBuilder withPriceFirst(long p) {
		this.priceFirst = p;
		return this;
	}

	public DealEventBuilder withPrices(long min, long max, long last) {
		this.priceMin = min;
		this.priceMax = max;
		this.priceLast = last;
		return this;
	}

	public DealEventBuilder crossVerified() {
		this.crossFlag = true;
		return this;
	}

	public DealEventBuilder singleSite() {
		this.crossFlag = false;
		this.sourceSites = null;
		return this;
	}

	public DealEventBuilder withSourceSites(String... sites) {
		this.sourceSites = Set.of(sites);
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

	public DealEventBuilder permanentlyExcluded() {
		this.permanentlyExcluded = true;
		return this;
	}

	public DealEventBuilder status(DealStatus s) {
		this.status = s;
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

	public DealEventBuilder lastSeen(String iso) {
		this.lastSeen = Instant.parse(iso);
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
		Set<String> resolvedSites = (sourceSites != null) ? sourceSites
				: (crossFlag ? Set.of(site, site + "|verifier") : Set.of(site));
		long pMin = (priceMin != null) ? priceMin : priceFirst;
		long pMax = (priceMax != null) ? priceMax : priceFirst;
		long pLast = (priceLast != null) ? priceLast : priceFirst;
		Instant ls = (lastSeen != null) ? lastSeen : firstSeen;
		return new DealEvent(variantId, unclassified, productCandidates, priceFirst, pMin, pMax, pLast,
				origin, resolvedSites, outlierFlag, permanentlyExcluded, status, firstSeen, ls, site, sourceUrl,
				appliedConditions);
	}
}
