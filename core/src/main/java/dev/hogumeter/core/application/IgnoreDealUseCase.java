package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.DealIgnoreEntity;
import dev.hogumeter.core.adapter.persistence.DealIgnoreRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemEntity;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.application.port.out.ReviewNotifier;
import dev.hogumeter.core.domain.deal.KeywordSuggester;
import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BM-07 사후학습(Q-22): 알림의 [무시] 버튼이 눌리면 이 딜을 노이즈로 기록하고, 같은 variant의 무시 제목들에서
 * 빈출 토큰을 {@link KeywordSuggester}가 제외 키워드 후보로 뽑아 KEYWORD_SUGGEST 큐를 만든다.
 *
 * <p><b>판단은 사람</b>(절대 원칙 2): 자동으로 제외 키워드를 넣지 않는다 — 후보를 <b>제안</b>할 뿐, 사용자가
 * 정책 패널에서 추가한다. 지어낸 규칙으로 딜을 조용히 지우지 않는다. 제목은 무시 시점 값을 박제한다(학습 입력).
 */
@Service
public class IgnoreDealUseCase {

	private final DealIgnoreRepository ignores;
	private final DealEventRepository dealEvents;
	private final DealEventSourceRepository sources;
	private final RawDealPostRepository rawPosts;
	private final AlertPolicyRepository policies;
	private final ReviewQueueItemRepository reviewQueue;
	private final ReviewNotifier reviewNotifier;
	private final KeywordSuggester suggester = new KeywordSuggester();

	public IgnoreDealUseCase(DealIgnoreRepository ignores, DealEventRepository dealEvents,
			DealEventSourceRepository sources, RawDealPostRepository rawPosts, AlertPolicyRepository policies,
			ReviewQueueItemRepository reviewQueue, ReviewNotifier reviewNotifier) {
		this.ignores = ignores;
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.rawPosts = rawPosts;
		this.policies = policies;
		this.reviewQueue = reviewQueue;
		this.reviewNotifier = reviewNotifier;
	}

	@Transactional
	public void ignore(long dealEventId) {
		if (ignores.existsByDealEventId(dealEventId)) {
			return; // 멱등 — 같은 알림을 두 번 눌러도 한 번만
		}
		DealEventEntity deal = dealEvents.findById(dealEventId).orElse(null);
		if (deal == null || deal.getVariantId() == null) {
			return; // 사라졌거나 미상 — 학습 대상 아님
		}
		String title = titleOf(dealEventId);
		if (title == null) {
			return; // 제목을 못 찾으면 학습 입력이 없다 — 지어내지 않는다
		}
		ignores.save(new DealIgnoreEntity(dealEventId, deal.getVariantId(), title));
		suggestKeywords(deal.getVariantId());
	}

	private void suggestKeywords(long variantId) {
		List<String> ignoredTitles = ignores.findByVariantId(variantId).stream()
				.map(DealIgnoreEntity::getTitle)
				.toList();
		suggester.suggest(ignoredTitles, knownExcludeKeywords(variantId))
				.ifPresent(item -> createSuggestion(variantId, item));
	}

	@SuppressWarnings("unchecked")
	private void createSuggestion(long variantId, ReviewQueueItem item) {
		List<String> candidates = (List<String>) item.payload().get("candidates");
		// 후보 집합이 곧 dedup 키다 — 같은 제안을 매 무시마다 다시 만들지 않는다. 새 토큰이 늘면 새 제안.
		String dedupKey = "kw:" + variantId + ":" + String.join("|", candidates);
		if (reviewQueue.findByDedupKey(dedupKey).isPresent()) {
			return;
		}
		ReviewQueueItemEntity saved = reviewQueue.save(new ReviewQueueItemEntity(ReviewQueueType.KEYWORD_SUGGEST,
				Map.of("variantId", variantId, "candidates", candidates), dedupKey));
		// 정보성 알림(승격 아님) — 사람이 정책 패널에서 추가한다("판단은 사람").
		reviewNotifier.notify(saved.getId(),
				"🔕 사후학습: 무시한 딜에 자주 나온 키워드 " + candidates + " — 정책 패널에서 제외 키워드로 추가하면 이 노이즈가 줄어듭니다.",
				false);
	}

	/** 이 딜의 <b>한 원문 제목</b>(학습 입력). 교차검증이면 여럿이지만 토큰화엔 하나로 충분하다. */
	private String titleOf(long dealEventId) {
		for (DealEventSourceEntity source : sources.findByDealEventId(dealEventId)) {
			String title = rawPosts.findById(source.getRawDealPostId()).map(RawDealPost::getTitle).orElse(null);
			if (title != null) {
				return title;
			}
		}
		return null;
	}

	private Set<String> knownExcludeKeywords(long variantId) {
		return policies.findByVariantId(variantId)
				.map(p -> Set.copyOf(p.getExcludeKeywords()))
				.orElseGet(Set::of);
	}
}
