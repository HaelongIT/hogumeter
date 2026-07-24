package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.dealset.DealSets;
import dev.hogumeter.core.domain.digest.DigestWindow;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * DIG-04 ① 딜 요약(docs/18) — "이번 창 최고 기회". 상태별 잣대가 다르다: <b>활성 딜은 {@code priceLast}</b>
 * (지금 살 수 있는 가격), <b>종료 딜은 {@code priceMin}</b>(지나간 기회, 생애 최저). 그 기회가가 가장 낮은
 * 딜 하나를 고른다.
 *
 * <p>대상은 {@link DealSets#occurrenceSet}(시간의 통계 자격) 중 <b>가시화 시각이 창 안</b>인 딜뿐이다 —
 * 섹션 ③과 같은 근사(가시화 시각 = {@code firstSeen}, docs/91 Q-81의 한계 그대로 상속). 창 안에 자격
 * 딜이 없으면 빈 결과({@code Optional.empty()}) — 지어내지 않는다.
 *
 * <p>상태 아이콘·검토 대기 딱지·SPARSE 사례 형식·basis 표시는 <b>렌더링(조립 단계)의 몫</b>이다 —
 * 이 유스케이스는 "어느 딜이 최고 기회인가 + 그 기회가"라는 좁은 계약만 진다({@link #bestOpportunity}가
 * 딜 자체를 돌려주므로 렌더링이 필요한 필드에 그대로 접근한다).
 */
@Service
public class ComputeDigestBestOpportunityUseCase {

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final VariantExcludeKeywords excludeKeywords;

	public ComputeDigestBestOpportunityUseCase(VariantRepository variants, DealEventRepository dealEvents,
			DealEventMapper mapper, VariantExcludeKeywords excludeKeywords) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.excludeKeywords = excludeKeywords;
	}

	public Optional<BestOpportunity> bestOpportunity(long variantId, DigestWindow window) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = excludeKeywords.filter(variantId, dealEvents.findByVariantId(variantId)).stream()
				.map(mapper::toDomain)
				.toList();
		return DealSets.occurrenceSet(deals).stream()
				.filter(d -> window.contains(d.firstSeen()))
				.map(BestOpportunity::of)
				.min(Comparator.comparingLong(BestOpportunity::opportunityPrice));
	}

	/**
	 * @param deal 최고 기회 딜(렌더링이 site·url·conditions·상태에 접근한다)
	 * @param opportunityPrice 상태별 잣대로 고른 기회가 — 활성이면 priceLast, 종료면 priceMin
	 * @param active 활성(ENDED 아님) 여부 — 아이콘·잣대 표기용
	 */
	public record BestOpportunity(DealEvent deal, long opportunityPrice, boolean active) {

		static BestOpportunity of(DealEvent deal) {
			boolean active = deal.status() != DealStatus.ENDED;
			long price = active ? deal.priceLast() : deal.priceMin();
			return new BestOpportunity(deal, price, active);
		}
	}
}
