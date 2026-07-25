package dev.hogumeter.core.domain.watch;

import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;

/**
 * WATCH(docs/17) 핀 자격(순수) = [표시된 딜 ∧ ENDED 아님 ∧ 자격 상실 아님 ∧ 기각 아님].
 *
 * <p>"표시된 딜"·"자격 상실"은 지어내지 않고 <b>이미 존재하는 정본 조건을 그대로 재사용</b>한다 —
 * {@code outlierFlag != NONE}이면 BM-06이 이미 기준가 표본·화면에서 뺀 딜이고(자격 상실),
 * {@code permanentlyExcluded}는 BM-05 AC-3의 사기·낚시 기각(영구 제외)이다. 새 조건을 만들면 두 판정이
 * 갈릴 위험이 있다 — 화면엔 안 뜨는데 핀은 되는 딜이 생긴다.
 */
public final class PinEligibility {

	private PinEligibility() {
	}

	public static boolean canPin(DealStatus status, OutlierFlag outlierFlag, boolean permanentlyExcluded) {
		return status != DealStatus.ENDED
				&& outlierFlag == OutlierFlag.NONE
				&& !permanentlyExcluded;
	}
}
