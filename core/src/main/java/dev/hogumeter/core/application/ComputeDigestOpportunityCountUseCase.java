package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import dev.hogumeter.core.domain.digest.DigestWindow;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * DIG-04 ③ 관찰 경과(docs/18) — "이번 창 +k / 누적 N"(기회 기준). 6개 섹션 중 가장 단순해 먼저 배선한다.
 *
 * <p><b>기회</b> = {@link DealSets#occurrenceSet}(시간의 통계 자격 — UPPER 제외, LOWER는 기각만 제외).
 * <b>가시화 시각</b>은 정확히는 "occurrenceSet 자격을 최초/재획득한 시각"(DIG-03)이지만, 그 자격
 * 전이를 별도로 기록하지 않으므로(이력 테이블 없음) <b>{@code firstSeen}으로 근사</b>한다 — 딜이
 * 자격을 잃었다 되찾는 재진입 케이스는 지금 못 잡는다(docs/91 Q-81에 한계로 남긴다).
 *
 * <p><b>SPLIT 미분리</b>: DIG-04는 "SPLIT은 목록 셀 집계로 variant당 1벌"이라 하므로, 수요축 값별로
 * 나누지 않고 그 variant의 딜을 전부 합산한다 — {@code GetBenchmarkUseCase}와 달리 여기선
 * {@code VariantDemandScope}를 부르지 않는다(의도적, 다이제스트만의 집계 방식).
 */
@Service
public class ComputeDigestOpportunityCountUseCase {

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final VariantExcludeKeywords excludeKeywords;

	public ComputeDigestOpportunityCountUseCase(VariantRepository variants, DealEventRepository dealEvents,
			DealEventMapper mapper, VariantExcludeKeywords excludeKeywords) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.excludeKeywords = excludeKeywords;
	}

	public OpportunityCount count(long variantId, DigestWindow window) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = excludeKeywords.filter(variantId, dealEvents.findByVariantId(variantId)).stream()
				.map(mapper::toDomain)
				.toList();
		List<DealEvent> occurrences = DealSets.occurrenceSet(deals);
		int cumulative = occurrences.size();
		int inWindow = (int) occurrences.stream().filter(d -> window.contains(d.firstSeen())).count();
		return new OpportunityCount(inWindow, cumulative);
	}

	/** @param inWindow 이번 창 +k  @param cumulative 누적 N */
	public record OpportunityCount(int inWindow, int cumulative) {
	}
}
