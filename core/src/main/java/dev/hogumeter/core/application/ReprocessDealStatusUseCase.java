package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-27 상태변화 재처리 — 수집기가 {@code raw_deal_post}에 업서트한 상태(SOLD_OUT/DELETED)를
 * {@code deal_event}로 전파한다(BM-01 AC-2의 "상태 변화는 기존 행에 반영"). {@code findUnprocessed}는
 * 링크된 원문을 다시 읽지 않으므로 이 별개 경로가 필요하다 — ingest(신규 원문)와 관심사 분리.
 *
 * <p>종료 규칙(다중 소스 정합·재현율 우선): 링크된 <b>모든</b> 원문이 종료됐을 때만 딜을 ENDED로 한다 —
 * 한 소스라도 ACTIVE면 다른 사이트에서 여전히 구매 가능하므로 종료가 아니다. last_seen은 종료 근거 시각으로
 * 갱신하되 뒤로 가지 않는다(단조).
 */
@Service
public class ReprocessDealStatusUseCase {

	/** raw_deal_post.status(문자열)의 "종료" 집합. */
	private static final Set<String> ENDED_STATUSES = Set.of("SOLD_OUT", "DELETED");
	/** 종료 전이 가능한 상태만 대상(NEW·ENDED 제외 → transitionTo 불변 보존). */
	private static final List<DealStatus> ENDABLE = List.of(DealStatus.ACTIVE, DealStatus.VERIFIED);

	private final DealEventRepository dealEvents;
	private final DealEventSourceRepository sources;
	private final RawDealPostRepository rawPosts;

	public ReprocessDealStatusUseCase(DealEventRepository dealEvents, DealEventSourceRepository sources,
			RawDealPostRepository rawPosts) {
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.rawPosts = rawPosts;
	}

	@Transactional
	public void reprocessEndedDeals() {
		for (DealEventEntity deal : dealEvents.findByStatusIn(ENDABLE)) {
			endIfAllSourcesEnded(deal);
		}
	}

	private void endIfAllSourcesEnded(DealEventEntity deal) {
		List<Long> rawIds = sources.findByDealEventId(deal.getId()).stream()
				.map(DealEventSourceEntity::getRawDealPostId)
				.toList();
		if (rawIds.isEmpty()) {
			return; // 근거 원문 없음 — 판단 불가
		}
		List<RawDealPost> posts = rawPosts.findAllById(rawIds);
		if (posts.isEmpty() || !posts.stream().allMatch(p -> ENDED_STATUSES.contains(p.getStatus()))) {
			return; // 아직 살아있는 소스 있음 → 종료 아님
		}
		Instant newestEvidence = posts.stream()
				.map(RawDealPost::getCapturedAt)
				.max(Instant::compareTo)
				.orElse(deal.getLastSeen());
		Instant lastSeen = newestEvidence.isAfter(deal.getLastSeen()) ? newestEvidence : deal.getLastSeen();
		deal.applyStatusChange(DealStatus.ENDED, lastSeen);
	}
}
