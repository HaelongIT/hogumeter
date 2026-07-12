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
import dev.hogumeter.core.domain.deal.OutlierDetector;
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
	private final EvaluateAlertOnDealUseCase alertEvaluation;
	private final Matcher matcher = new Matcher();
	private final DealMergePolicy mergePolicy = new DealMergePolicy();
	private final OutlierDetector outlierDetector = new OutlierDetector();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	/** SPARSE(n<5) 구간은 IQR 불안정 → 이상치 판정 보류(폴백 컷은 Q-14 후속). 이 이상에서만 IQR 판정. */
	private static final int OUTLIER_MIN_DISTRIBUTION = 5;

	public IngestDealsUseCase(RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, DealEventMapper mapper, CatalogProjection catalog,
			ReviewQueueItemRepository reviewQueue, EvaluateAlertOnDealUseCase alertEvaluation) {
		this.rawPosts = rawPosts;
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.mapper = mapper;
		this.catalog = catalog;
		this.reviewQueue = reviewQueue;
		this.alertEvaluation = alertEvaluation;
	}

	@Transactional
	public IngestReport ingestPending() {
		List<ProductMatchSpec> catalogSpecs = catalog.catalog();
		AliasDictionary dictionary = catalog.aliasDictionary();
		Tally tally = new Tally();
		for (RawDealPost post : rawPosts.findUnprocessed()) {
			ingestOne(post, catalogSpecs, dictionary, tally);
		}
		return tally.toReport();
	}

	private void ingestOne(RawDealPost post, List<ProductMatchSpec> catalogSpecs, AliasDictionary dictionary,
			Tally tally) {
		if (post.getHeadlinePrice() == null) {
			tally.skippedNoPrice++;
			return; // BM-02 AC-3: 가격 없음 → 스킵(deal_event 미생성)
		}
		MatchResult match = matcher.match(post.getTitle(), catalogSpecs, dictionary);
		switch (match.tier()) {
			case CONFIRMED -> {
				tally.confirmed++;
				if (confirmDeal(post, match.variantId()) == DispatchOutcome.SENT) {
					tally.firstAlertsSent++;
				}
			}
			case CANDIDATE -> {
				tally.candidate++;
				enqueueForReview(post, match);
			}
			case UNKNOWN -> {
				tally.unknown++;
				enqueueForReview(post, match);
			}
			case REJECTED -> tally.rejected++; // 무관 — 스킵
		}
	}

	/** @return 이 딜에 대한 알림 판정 결과 — 첫 알림이 실제로 나갔는지(SENT) 세기 위함(Q-57 ③). */
	private DispatchOutcome confirmDeal(RawDealPost post, long variantId) {
		DealEvent candidate = candidateFrom(post, variantId);

		for (DealEventEntity existing : dealEvents.findByVariantId(variantId)) {
			DealEvent existingDomain = mapper.toDomain(existing);
			if (mergePolicy.canMerge(existingDomain, candidate, params)) {
				DealEvent merged = mergePolicy.merge(existingDomain, candidate);
				existing.applyMerge(merged.priceFirst(), merged.priceMin(), merged.priceMax(), merged.priceLast(),
						merged.crossVerified(), merged.status(), merged.firstSeen(), merged.lastSeen());
				sources.save(new DealEventSourceEntity(existing.getId(), post.getId(), post.getSite()));
				return alertEvaluation.evaluate(variantId, existing.getId(), mapper.toDomain(existing)); // 흡수 후속 판정
			}
		}

		DealEventEntity created = dealEvents.save(new DealEventEntity(variantId, false, null,
				candidate.priceFirst(), candidate.priceMin(), candidate.priceMax(), candidate.priceLast(),
				candidate.origin(), candidate.crossVerified(), candidate.outlierFlag(), false,
				candidate.status(), candidate.firstSeen(), candidate.lastSeen()));
		sources.save(new DealEventSourceEntity(created.getId(), post.getId(), post.getSite()));
		classifyOutlier(created, variantId);
		return alertEvaluation.evaluate(variantId, created.getId(), mapper.toDomain(created));
	}

	/** BM-05 배선: 신규 딜을 variant 분포에 대해 판정(유입 1회·영속, C-4). LOWER는 reviewQueue(OUTLIER_LOWER). */
	private void classifyOutlier(DealEventEntity created, long variantId) {
		List<Long> distribution = dealEvents.findByVariantId(variantId).stream()
				.map(DealEventEntity::getPriceFirst)
				.toList();
		if (distribution.size() < OUTLIER_MIN_DISTRIBUTION) {
			return;
		}
		OutlierFlag flag = outlierDetector.classify(created.getPriceFirst(), distribution, params);
		if (flag == OutlierFlag.NONE) {
			return;
		}
		created.setOutlierFlag(flag);
		if (flag == OutlierFlag.LOWER) {
			upsertReviewItem(ReviewQueueType.OUTLIER_LOWER, Map.of(
					"priceFirst", created.getPriceFirst(),
					"dealEventId", created.getId()), "o:" + created.getId());
		}
	}

	private DealEvent candidateFrom(RawDealPost post, long variantId) {
		long price = post.getHeadlinePrice();
		Instant firstSeen = post.getPostedAt() != null ? post.getPostedAt() : post.getCapturedAt();
		// 새 딜은 조건 태그가 없다 — PreserveAppliedConditionsUseCase가 ingest 뒤에 원문에서 끌어올린다.
		return new DealEvent(variantId, false, Set.of(), price, price, price, price, Origin.LIVE,
				Set.of(post.getSite()), OutlierFlag.NONE, false, DealStatus.fromRawPostStatus(post.getStatus()),
				firstSeen, firstSeen, post.getSite(), post.getUrl(), Set.of());
	}

	private void enqueueForReview(RawDealPost post, MatchResult match) {
		ReviewQueueItem item = new ReviewQueueItem(ReviewQueueType.UNCLASSIFIED, Map.of(
				"title", post.getTitle(),
				"rawDealPostId", post.getId(),
				"productCandidates", List.copyOf(match.productCandidates())));
		// 미상 원문은 딜로 링크되지 않아 매 틱 다시 스캔된다(Q-27 ④) — 새 행 대신 같은 근거를 접어 센다.
		upsertReviewItem(item.type(), item.payload(), "u:" + post.getId());
	}

	/**
	 * 같은 근거(dedup_key)가 이미 큐에 있으면 새 행을 만들지 않고 재적재를 센다(Q-27 ④). 없으면 새로 넣는다.
	 * 접어서 세는 이유: 조용히 지우면 결함이 사라진 것처럼 보인다 — occurrences가 크다는 것이 곧 "재처리
	 * 멱등이 없다"는 증거다(읽기 모델·web이 그 수를 드러낸다).
	 */
	private void upsertReviewItem(ReviewQueueType type, Map<String, Object> payload, String dedupKey) {
		reviewQueue.findByDedupKey(dedupKey).ifPresentOrElse(
				ReviewQueueItemEntity::recordRecurrence,
				() -> reviewQueue.save(new ReviewQueueItemEntity(type, payload, dedupKey)));
	}

	/** 수집 한 회의 가변 집계기 — 루프가 끝나면 불변 {@link IngestReport}로 굳힌다. */
	private static final class Tally {
		int confirmed;
		int candidate;
		int unknown;
		int rejected;
		int skippedNoPrice;
		int firstAlertsSent;

		IngestReport toReport() {
			return new IngestReport(confirmed, candidate, unknown, rejected, skippedNoPrice, firstAlertsSent);
		}
	}
}
