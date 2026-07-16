package dev.hogumeter.core.domain.dealset;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.DealTags;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
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

	/**
	 * 수요축 범위(Q-66 ①, 확정본 §40·41). <b>분리(SPLIT)</b>면 그 축 값의 딜만 남긴다 — 값별로 분포가
	 * 갈리는 게 분리의 정의다. <b>묶음(GROUPED)</b>이면 전부 그대로 — 모든 값이 한 분포를 공유한다.
	 *
	 * <p><b>값 미상(null) 딜은 SPLIT에서 자동으로 빠진다</b> — null은 어떤 요청값과도 같지 않기 때문이다.
	 * 그게 §41의 "미상 버킷은 기준가 계산 제외"이고, 별도 분기가 필요 없다. 미상 딜을 아무 분포에나
	 * 넣으면 그 분포가 조용히 오염된다 — 모르면 빼는 쪽이 정직하다.
	 *
	 * @param demandAxisValue SPLIT일 때 볼 축 값. 호출자가 그 존재를 보장한다(없으면 조회 자체가 거절된다).
	 */
	public static List<DealEvent> demandScope(List<DealEvent> deals, DemandAxisMode mode, String demandAxisValue) {
		if (mode != DemandAxisMode.SPLIT) {
			return List.copyOf(deals);
		}
		return deals.stream()
				.filter(d -> demandAxisValue.equals(d.demandAxisValue()))
				.toList();
	}

	/**
	 * 값의 통계(기준가·P25·성적표). 이상치 미해당(승격 LOWER=NONE 자동 포함)·영구제외 제외.
	 * <p>배송비미상 딜도 제외한다(Q-46 ②): 저장가가 실제보다 낮은 하한이라 median/P25를 아래로 끌어
	 * <b>없는 굿딜을 있다고</b> 말하게 된다(놓침 아니라 오알림, 원칙3). 발생·신호 집합엔 남긴다 — 실제 딜이다.
	 */
	public static List<DealEvent> pricingSet(List<DealEvent> deals) {
		return deals.stream()
				.filter(DealSets::isClassified)
				.filter(d -> d.outlierFlag() == OutlierFlag.NONE)
				.filter(d -> !d.permanentlyExcluded())
				.filter(d -> !d.hasCondition(DealTags.SHIPPING_UNKNOWN))
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
