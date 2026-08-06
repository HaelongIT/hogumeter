package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.CatalogProjection;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.application.port.out.ReviewNotifier;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemEntity;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealMergePolicy;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.NewProductSources;
import dev.hogumeter.core.domain.deal.OutlierDetector;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.matching.AliasDictionary;
import dev.hogumeter.core.domain.matching.MatchResult;
import dev.hogumeter.core.domain.matching.Matcher;
import dev.hogumeter.core.domain.matching.ProductMatchSpec;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import dev.hogumeter.core.domain.watch.PinState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final VariantDemandScope demandScope;
	private final ReviewNotifier reviewNotifier;
	private final WatchItemRepository watchItems;
	private final CurrentPriceProvider currentPriceProvider;
	private final Matcher matcher = new Matcher();
	private final DealMergePolicy mergePolicy = new DealMergePolicy();
	private final OutlierDetector outlierDetector = new OutlierDetector();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	/** SPARSE(n<5) 구간은 IQR 불안정 → Tukey 대신 현재가 대비 폴백(AC-5, Q-14). 이 이상에서만 IQR 판정. */
	private static final int OUTLIER_MIN_DISTRIBUTION = 5;
	/** Q-14 잠정값(±50%) — 운영자 미승인이라 BenchmarkParams(승인 seam)와 분리해 여기 주입. */
	private static final BigDecimal ABSURDITY_RATIO = new BigDecimal("0.5");

	public IngestDealsUseCase(RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, DealEventMapper mapper, CatalogProjection catalog,
			ReviewQueueItemRepository reviewQueue, EvaluateAlertOnDealUseCase alertEvaluation,
			VariantDemandScope demandScope, ReviewNotifier reviewNotifier, WatchItemRepository watchItems,
			CurrentPriceProvider currentPriceProvider) {
		this.rawPosts = rawPosts;
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.mapper = mapper;
		this.catalog = catalog;
		this.reviewQueue = reviewQueue;
		this.alertEvaluation = alertEvaluation;
		this.demandScope = demandScope;
		this.reviewNotifier = reviewNotifier;
		this.watchItems = watchItems;
		this.currentPriceProvider = currentPriceProvider;
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
		if (!NewProductSources.acceptsAsNewProduct(post.getSite())) {
			tally.skippedForeignSource++;
			return; // 중고 마켓·모르는 소스는 신품 기준가에 넣지 않는다 — 가격 유무보다 먼저 가른다
		}
		if (post.getHeadlinePrice() == null) {
			tally.skippedNoPrice++;
			return; // BM-02 AC-3: 가격 없음 → 스킵(deal_event 미생성)
		}
		MatchResult match = matcher.match(post.getTitle(), catalogSpecs, dictionary);
		switch (match.tier()) {
			case CONFIRMED -> {
				tally.confirmed++;
				ConfirmResult result = confirmDeal(post, match.variantId(), match.demandAxisValue());
				if (result.reopened()) {
					tally.reopenedDealIds.add(result.dealEventId());
				}
				else if (result.merged()) {
					tally.mergedDealIds.add(result.dealEventId());
				}
				if (result.outcome() == DispatchOutcome.SENT) {
					tally.firstAlertsSent++;
				}
				else if (result.outcome() == DispatchOutcome.HELD) {
					// 방해금지로 보류 — 지금은 플러시가 없어 유실된다(Q-20 ②). 세어서 보이게 한다.
					tally.heldAlerts++;
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

	/**
	 * variant가 확정된 딜을 병합/신규 저장한다. 자동 매칭(CONFIRMED)뿐 아니라 사람이 미상 큐에서 variant를
	 * 골라 승격할 때도 이 메서드를 그대로 쓴다({@link ResolveReviewItemUseCase} — Q-15 ①) — 딜 생성·병합
	 * 규칙은 한 곳(정본)이어야 한다. 알림 판정(SENT/HELD, Q-57 ③)·병합·부활(Q-13·DN-C1) 여부를
	 * {@link ConfirmResult}로 반환한다 — 파이프라인 카운팅은 호출자(Tally)의 몫이라 여기 두지 않는다.
	 */
	ConfirmResult confirmDeal(RawDealPost post, long variantId, String demandAxisValue) {
		DealEvent candidate = candidateFrom(post, variantId, demandAxisValue);

		for (DealEventEntity existing : dealEvents.findByVariantId(variantId)) {
			DealEvent existingDomain = mapper.toDomain(existing);
			if (mergePolicy.canMerge(existingDomain, candidate, params)) {
				DealEvent merged = mergePolicy.merge(existingDomain, candidate);
				existing.applyMerge(merged.priceFirst(), merged.priceMin(), merged.priceMax(), merged.priceLast(),
						merged.crossVerified(), merged.status(), merged.firstSeen(), merged.lastSeen());
				sources.save(new DealEventSourceEntity(existing.getId(), post.getId(), post.getSite()));
				// Q-83 ①(2026-07-30): 이 딜에 ACTIVE 핀이 있으면 앵커를 방금 병합된 최신 원문으로 승계한다.
				Optional<WatchItemEntity> activePin = watchItems.findByDealEventIdAndState(existing.getId(),
						PinState.ACTIVE);
				activePin.ifPresent(item -> item.updateAnchorPostId(post.getId()));
				// Q-13: 흡수는 첫 알림을 다시 내지 않는다 — priceFirst는 병합으로 안 바뀌므로 같은 트리거를
				// 재평가하면 매번 다시 SEND_NOW가 날 수 있었다(텔레그램이 켜지면 중복 문자로 드러날 결함).
				// 대신 후속(AL-03) 대상으로만 기록한다 — PipelineScheduler가 FollowUpAlertUseCase로 넘기고,
				// 그쪽은 "첫 알림이 이미 나간 딜에만·종류당 1회"로 멱등이라 중복 병합에도 안전하다.
				//
				// DN-C1: 잠정 종료였던 딜이 되살아난 것(ENDED→ACTIVE)은 **교차검증이 아니라 부활**이다.
				// 부류가 다른 사실을 한 목록으로 흘리면 사람이 "다시 살아났다"를 "검증됐다"로 읽는다.
				boolean reopened = existingDomain.status() == DealStatus.ENDED
						&& merged.status() != DealStatus.ENDED;
				if (reopened) {
					// Q-83 ⑤(2026-07-30): 부활 = 전이 없음 + 미응답 플래그(핀 상태는 그대로 ACTIVE).
					activePin.ifPresent(WatchItemEntity::flagRevivalUnacknowledged);
				}
				return new ConfirmResult(DispatchOutcome.NO_ALERT, existing.getId(), true, reopened);
			}
		}

		DealEventEntity created = dealEvents.save(new DealEventEntity(variantId, false, null,
				candidate.priceFirst(), candidate.priceMin(), candidate.priceMax(), candidate.priceLast(),
				candidate.origin(), candidate.crossVerified(), candidate.outlierFlag(), false,
				candidate.status(), candidate.firstSeen(), candidate.lastSeen(), candidate.demandAxisValue()));
		sources.save(new DealEventSourceEntity(created.getId(), post.getId(), post.getSite()));
		classifyOutlier(created, variantId);
		enqueueIfDemandUnknown(post, created, variantId); // Q-66 ① E
		DispatchOutcome outcome = alertEvaluation.evaluate(variantId, created.getId(), mapper.toDomain(created));
		return new ConfirmResult(outcome, created.getId(), false, false);
	}

	/**
	 * @param outcome 알림 판정(파이프라인 카운팅용, Q-57 ③)
	 * @param dealEventId 확정된(생성 또는 병합) 딜의 id
	 * @param merged 기존 딜에 병합됐나(true) 새로 생성됐나(false)
	 * @param reopened 병합이면서 그 병합이 부활(ENDED→비ENDED)이었나 — merged=false면 항상 false
	 */
	record ConfirmResult(DispatchOutcome outcome, long dealEventId, boolean merged, boolean reopened) {
	}

	/** BM-05 배선: 신규 딜을 variant 분포에 대해 판정(유입 1회·영속, C-4). LOWER는 reviewQueue(OUTLIER_LOWER). */
	private void classifyOutlier(DealEventEntity created, long variantId) {
		List<Long> distribution = dealEvents.findByVariantId(variantId).stream()
				.map(DealEventEntity::getPriceFirst)
				.toList();
		OutlierFlag flag;
		if (distribution.size() < OUTLIER_MIN_DISTRIBUTION) {
			// AC-5 폴백: Tukey는 표본이 적으면 불안정하다 — 현재가가 있으면 그 대비로만 비상식을 가른다.
			// 현재가도 없으면(네이버 키 미발급, Q-3) 판정 근거가 아예 없다 — 조용히 스킵(지어내지 않는다).
			Long currentPrice = currentPriceProvider.currentPriceFor(variantId);
			if (currentPrice == null) {
				return;
			}
			flag = outlierDetector.classifyVsCurrent(created.getPriceFirst(), currentPrice, ABSURDITY_RATIO);
		}
		else {
			flag = outlierDetector.classify(created.getPriceFirst(), distribution, params);
		}
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

	/**
	 * Q-66 ① E(확정본 §41): 분리 제품인데 제목에서 수요축 값을 판별 못 한 딜을 <b>승격 큐에 올려</b>
	 * 사람이 분류하게 한다. 이 딜은 이미 SPLIT 분포에서 빠지지만(정직), 큐에 뜨지 않으면 사람이 볼 수 없다 —
	 * "놓친 것을 못 보면 유실"이다. 묶음 제품이나 값을 아는 딜은 대상이 아니다.
	 *
	 * <p>dedup_key로 접는다(Q-27 ④) — 같은 딜이 매 틱 다시 오지는 않지만(링크되면 findUnprocessed가 거른다),
	 * 접힘 계약을 일관되게 지킨다. 근거는 제목·딜 id·왜 미상인지.
	 */
	private void enqueueIfDemandUnknown(RawDealPost post, DealEventEntity created, long variantId) {
		if (created.getDemandAxisValue() != null || demandScope.modeOf(variantId) != DemandAxisMode.SPLIT) {
			return;
		}
		upsertReviewItem(ReviewQueueType.DEMAND_UNKNOWN, Map.of(
				"title", post.getTitle(),
				"dealEventId", created.getId(),
				"reason", "제목에서 수요축 값을 판별하지 못했습니다"), "dv:" + created.getId());
	}

	private DealEvent candidateFrom(RawDealPost post, long variantId, String demandAxisValue) {
		long price = post.getHeadlinePrice();
		Instant firstSeen = post.getPostedAt() != null ? post.getPostedAt() : post.getCapturedAt();
		// 새 딜은 조건 태그가 없다 — PreserveAppliedConditionsUseCase가 ingest 뒤에 원문에서 끌어올린다.
		// 수요축 값은 매칭이 제목에서 판별한 것을 그대로 싣는다(Q-66 ①). null = 값 미상 — 지어내지 않는다.
		// dealEventId는 저장 전이라 아직 없다(자리표시자 0) — confirmDeal이 실제 저장 후 mapper.toDomain(created)로
		// 진짜 id를 다시 채운 값을 쓴다. 이 값 자체는 읽히지 않는다.
		// origin은 raw_deal_post.origin을 그대로 옮긴다(REG-04, Q-87) — 과거엔 Origin.LIVE 하드코딩이라
		// BACKFILL 경로가 core에 닿을 수조차 없었다. 병합 시 LIVE 승격은 DealMergePolicy가 이미 담당.
		return new DealEvent(variantId, false, Set.of(), price, price, price, price, Origin.valueOf(post.getOrigin()),
				Set.of(post.getSite()), OutlierFlag.NONE, false, DealStatus.fromRawPostStatus(post.getStatus()),
				firstSeen, firstSeen, post.getSite(), post.getUrl(), Set.of(), demandAxisValue, 0L);
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
				() -> {
					// 새로 생긴 항목만 알린다(재적재/반복은 아님 — dedup가 여기서 갈린다). 텔레그램이면 버튼과 함께.
					ReviewQueueItemEntity saved = reviewQueue.save(new ReviewQueueItemEntity(type, payload, dedupKey));
					reviewNotifier.notify(saved.getId(), reviewSummary(type, payload),
							type == ReviewQueueType.OUTLIER_LOWER);
				});
	}

	/** 미상 큐 알림 한 줄. OUTLIER_LOWER는 제목이 없어 가격으로, 나머지는 제목으로 식별한다(payload 그대로). */
	private static String reviewSummary(ReviewQueueType type, Map<String, Object> payload) {
		return switch (type) {
			case OUTLIER_LOWER -> "🔍 이상치 의심 딜 (" + payload.get("priceFirst") + "원) — 정상 딜이면 승격, 사기·낚시면 기각하세요.";
			case DEMAND_UNKNOWN -> "🔍 수요축 미상: " + payload.get("title") + " — 분류가 필요합니다(기각만 가능).";
			case UNCLASSIFIED -> "🔍 미상 딜: " + payload.get("title") + " — 제품 매칭 실패(기각만 가능).";
			// KEYWORD_SUGGEST(BM-07 사후학습)는 이 수집 경로에서 만들지 않는다 — 방어적 분기(Q-22 배선 시 문구 조정).
			case KEYWORD_SUGGEST -> "🔍 키워드 제안 검토가 필요합니다.";
		};
	}

	/** 수집 한 회의 가변 집계기 — 루프가 끝나면 불변 {@link IngestReport}로 굳힌다. */
	private static final class Tally {
		int confirmed;
		int candidate;
		int unknown;
		int rejected;
		int skippedNoPrice;
		int firstAlertsSent;
		int heldAlerts;
		int skippedForeignSource;
		final List<Long> mergedDealIds = new ArrayList<>();
		final List<Long> reopenedDealIds = new ArrayList<>();

		IngestReport toReport() {
			return new IngestReport(confirmed, candidate, unknown, rejected, skippedNoPrice, firstAlertsSent,
					heldAlerts, skippedForeignSource, mergedDealIds, reopenedDealIds);
		}
	}
}
