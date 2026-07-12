package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.PriceEvidence;
import dev.hogumeter.core.domain.deal.PriceRefresh;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-27 ① 가격 변경 재처리 — 수집기가 {@code raw_deal_post}에 업서트한 새 가격을 {@code deal_event}로
 * 전파한다(BM-01 AC-2). {@code findUnprocessed()}는 이미 링크된 원문을 다시 읽지 않으므로 이 별개
 * 경로가 필요하다 — ingest(신규 원문)와 관심사 분리.
 *
 * <p>산술은 전부 {@link PriceRefresh}(순수)에 있다. 여기는 증거를 모으고 결과를 쓰는 IO뿐이다.
 *
 * <p>{@code priceFirst}·{@code firstSeen}·{@code status}는 건드리지 않는다 — 발생 시각·발생 가격은
 * 불변(C-2)이고, 종료 판정은 {@link ReprocessDealStatusUseCase}의 몫이다.
 */
@Service
public class ReprocessDealPricesUseCase {

	/** 종료된 딜의 가격은 더 이상 움직이지 않는다. */
	private static final Set<DealStatus> REFRESHABLE = Set.of(DealStatus.ACTIVE, DealStatus.VERIFIED);

	/** 원문이 아직 살아 있는가 — 이 상태의 가격만 "지금"이 될 수 있다. */
	private static final String ACTIVE_POST = "ACTIVE";

	private final DealEventRepository dealEvents;
	private final DealEventSourceRepository sources;
	private final RawDealPostRepository rawPosts;
	private final DealEventMapper mapper;

	public ReprocessDealPricesUseCase(DealEventRepository dealEvents, DealEventSourceRepository sources,
			RawDealPostRepository rawPosts, DealEventMapper mapper) {
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.rawPosts = rawPosts;
		this.mapper = mapper;
	}

	/** @return 이번에 가격이 실제로 바뀐 딜 id — 후속 알림(AL-03 PRICE_CHANGED)의 대상이다(Q-67). */
	@Transactional
	public List<Long> reprocessPriceChanges() {
		List<Long> changed = new ArrayList<>();
		for (DealEventEntity deal : dealEvents.findByStatusIn(REFRESHABLE)) {
			if (refresh(deal)) {
				changed.add(deal.getId());
			}
		}
		return changed;
	}

	/** @return 가격이 바뀌어 반영했으면 true, 변화 없으면 false(미기록). */
	private boolean refresh(DealEventEntity deal) {
		List<PriceEvidence> evidence = evidenceFor(deal);
		DealEvent current = mapper.toDomain(deal);

		Optional<PriceRefresh> result = PriceRefresh.from(current, evidence);
		result.ifPresent(refresh -> deal.applyMerge(
				deal.getPriceFirst(), // 발생 가격은 불변 — 기준가 분포가 그 위에 서 있다
				refresh.priceMin(),
				refresh.priceMax(),
				refresh.priceLast(),
				current.crossVerified(), // 소스 수에서 파생된 값. 여기서 바뀌지 않는다
				deal.getStatus(), // 종료 판정은 ReprocessDealStatusUseCase의 몫
				deal.getFirstSeen(),
				refresh.lastSeen()));
		return result.isPresent();
	}

	private List<PriceEvidence> evidenceFor(DealEventEntity deal) {
		return sources.findByDealEventId(deal.getId()).stream()
				.map(DealEventSourceEntity::getRawDealPostId)
				.map(rawPosts::findById)
				.flatMap(Optional::stream)
				// 가격 없는 원문은 증거가 아니다(BM-02 AC-3). "0원"으로 읽으면 기준가가 무너진다.
				.filter(post -> post.getHeadlinePrice() != null)
				.map(ReprocessDealPricesUseCase::toEvidence)
				.toList();
	}

	private static PriceEvidence toEvidence(RawDealPost post) {
		return new PriceEvidence(post.getHeadlinePrice(), post.getCapturedAt(), ACTIVE_POST.equals(post.getStatus()));
	}
}
