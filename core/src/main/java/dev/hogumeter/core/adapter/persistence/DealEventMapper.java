package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.deal.DealEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * deal_event 엔티티 → 리치 도메인 {@link DealEvent} 재구성. sourceSites·대표 site/sourceUrl은
 * deal_event_source(→ raw_deal_post.url)에서 복원한다. crossVerified()는 sourceSites 수로 파생.
 */
@Component
public class DealEventMapper {

	private final DealEventSourceRepository sources;
	private final RawDealPostRepository rawPosts;

	public DealEventMapper(DealEventSourceRepository sources, RawDealPostRepository rawPosts) {
		this.sources = sources;
		this.rawPosts = rawPosts;
	}

	public DealEvent toDomain(DealEventEntity e) {
		List<DealEventSourceEntity> src = sources.findByDealEventId(e.getId());
		Set<String> siteSet = src.stream()
				.map(DealEventSourceEntity::getSite)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		String site = "";
		String sourceUrl = "";
		if (!src.isEmpty()) {
			DealEventSourceEntity representative = src.get(0);
			site = representative.getSite();
			sourceUrl = rawPosts.findById(representative.getRawDealPostId())
					.map(RawDealPost::getUrl).orElse("");
		}

		Set<Long> candidates = e.getProductCandidates() == null ? Set.of() : Set.copyOf(e.getProductCandidates());
		Set<String> conditions = e.getAppliedConditions() == null ? Set.of() : Set.copyOf(e.getAppliedConditions());

		return new DealEvent(e.getVariantId(), e.isUnclassified(), candidates,
				e.getPriceFirst(), e.getPriceMin(), e.getPriceMax(), e.getPriceLast(),
				e.getOrigin(), siteSet, e.getOutlierFlag(), e.isPermanentlyExcluded(), e.getStatus(),
				e.getFirstSeen(), e.getLastSeen(), site, sourceUrl, conditions);
	}
}
