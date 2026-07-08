package dev.hogumeter.core.domain.dealset;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.util.List;

/**
 * docs/03 3-1 세 집합(순수). "유효 딜" 단일 어휘를 값/시간/지금 세 질문별로 분리 명명한다.
 * 세 집합 모두 미상(unclassified·variantId null)은 제외. LOWER 3상태는 (outlierFlag, permanentlyExcluded)로 도출.
 * <p>키워드 무히트(Q-28)·선택 축값(C-6)·배치유보(PENDING_BATCH, C-4)·신선도(signalSet)는 아직 이 술어에 미포함 —
 * DealEvent에 상태가 없어 다운스트림/후속 seam으로 분리. 여기선 DealEvent 필드로 도출 가능한 자격만.
 */
public final class DealSets {

	private DealSets() {
	}

	/** 값의 통계(기준가·P25·성적표). 이상치 미해당(승격 LOWER=NONE 자동 포함)·영구제외 제외. */
	public static List<DealEvent> pricingSet(List<DealEvent> deals) {
		return deals.stream()
				.filter(DealSets::isClassified)
				.filter(d -> d.outlierFlag() == OutlierFlag.NONE)
				.filter(d -> !d.permanentlyExcluded())
				.toList();
	}

	/** 시간의 통계(CAD 주기, 정체성 축). UPPER 제외, LOWER는 기각(영구제외)만 제외 → 미확정 LOWER 포함. */
	public static List<DealEvent> occurrenceSet(List<DealEvent> deals) {
		return deals.stream()
				.filter(DealSets::isClassified)
				.filter(d -> d.outlierFlag() != OutlierFlag.UPPER)
				.filter(d -> !(d.outlierFlag() == OutlierFlag.LOWER && d.permanentlyExcluded()))
				.toList();
	}

	/**
	 * 지금의 단정(신호등·상시 표시)의 DealEvent 유래 자격: 비이상치 + ENDED 아님.
	 * 신선도 자격(관측시계 기반, docs/03 3-2)은 시간좌표가 필요해 SIG가 추가 필터로 적용한다.
	 */
	public static List<DealEvent> signalSet(List<DealEvent> deals) {
		return deals.stream()
				.filter(DealSets::isClassified)
				.filter(d -> d.outlierFlag() == OutlierFlag.NONE)
				.filter(d -> d.status() != DealStatus.ENDED)
				.toList();
	}

	private static boolean isClassified(DealEvent deal) {
		return deal.variantId() != null && !deal.unclassified();
	}
}
