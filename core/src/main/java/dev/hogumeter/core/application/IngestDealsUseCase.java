package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.CatalogProjection;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemEntity;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealMergePolicy;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.matching.AliasDictionary;
import dev.hogumeter.core.domain.matching.MatchResult;
import dev.hogumeter.core.domain.matching.Matcher;
import dev.hogumeter.core.domain.matching.ProductMatchSpec;
import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 파이프라인 배선(BM-03·04). 미처리 raw_deal_post를 매칭→병합→deal_event 저장.
 * CONFIRMED는 variant 배정 후 병합/신규, 애매(CANDIDATE·미상)는 reviewQueue(UNCLASSIFIED), 가격 없음/무관은 스킵.
 * 이상치 판정(BM-05)·알림(AL)은 다음 슬라이스에서 이 파이프라인에 연결.
 */
@Service
public class IngestDealsUseCase {

	private final RawDealPostRepository rawPosts;
	private final DealEventRepository dealEvents;
	private final DealEventSourceRepository sources;
	private final DealEventMapper mapper;
	private final CatalogProjection catalog;
	private final ReviewQueueItemRepository reviewQueue;
	private final Matcher matcher = new Matcher();
	private final DealMergePolicy mergePolicy = new DealMergePolicy();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	public IngestDealsUseCase(RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, DealEventMapper mapper, CatalogProjection catalog,
			ReviewQueueItemRepository reviewQueue) {
		this.rawPosts = rawPosts;
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.mapper = mapper;
		this.catalog = catalog;
		this.reviewQueue = reviewQueue;
	}

	@Transactional
	public void ingestPending() {
		List<ProductMatchSpec> catalogSpecs = catalog.catalog();
		AliasDictionary dictionary = catalog.aliasDictionary();
		for (RawDealPost post : rawPosts.findUnprocessed()) {
			ingestOne(post, catalogSpecs, dictionary);
		}
	}

	private void ingestOne(RawDealPost post, List<ProductMatchSpec> catalogSpecs, AliasDictionary dictionary) {
		if (post.getHeadlinePrice() == null) {
			return; // BM-02 AC-3: 가격 없음 → 스킵(deal_event 미생성)
		}
		MatchResult match = matcher.match(post.getTitle(), catalogSpecs, dictionary);
		switch (match.tier()) {
			case CONFIRMED -> confirmDeal(post, match.variantId());
			case CANDIDATE, UNKNOWN -> enqueueForReview(post, match);
			case REJECTED -> { /* 무관 — 스킵 */ }
		}
	}

	private void confirmDeal(RawDealPost post, long variantId) {
		DealEvent candidate = candidateFrom(post, variantId);

		for (DealEventEntity existing : dealEvents.findByVariantId(variantId)) {
			DealEvent existingDomain = mapper.toDomain(existing);
			if (mergePolicy.canMerge(existingDomain, candidate, params)) {
				DealEvent merged = mergePolicy.merge(existingDomain, candidate);
				existing.applyMerge(merged.priceFirst(), merged.priceMin(), merged.priceMax(), merged.priceLast(),
						merged.crossVerified(), merged.status(), merged.firstSeen(), merged.lastSeen());
				sources.save(new DealEventSourceEntity(existing.getId(), post.getId(), post.getSite()));
				return;
			}
		}

		DealEventEntity created = dealEvents.save(new DealEventEntity(variantId, false, null,
				candidate.priceFirst(), candidate.priceMin(), candidate.priceMax(), candidate.priceLast(),
				candidate.origin(), candidate.crossVerified(), candidate.outlierFlag(), false,
				candidate.status(), candidate.firstSeen(), candidate.lastSeen()));
		sources.save(new DealEventSourceEntity(created.getId(), post.getId(), post.getSite()));
	}

	private DealEvent candidateFrom(RawDealPost post, long variantId) {
		long price = post.getHeadlinePrice();
		Instant firstSeen = post.getPostedAt() != null ? post.getPostedAt() : post.getCapturedAt();
		return new DealEvent(variantId, false, Set.of(), price, price, price, price, Origin.LIVE,
				Set.of(post.getSite()), OutlierFlag.NONE, false, DealStatus.ACTIVE, firstSeen, firstSeen,
				post.getSite(), post.getUrl());
	}

	private void enqueueForReview(RawDealPost post, MatchResult match) {
		ReviewQueueItem item = new ReviewQueueItem(ReviewQueueType.UNCLASSIFIED, Map.of(
				"title", post.getTitle(),
				"rawDealPostId", post.getId(),
				"productCandidates", List.copyOf(match.productCandidates())));
		reviewQueue.save(new ReviewQueueItemEntity(item.type(), item.payload()));
	}
}
