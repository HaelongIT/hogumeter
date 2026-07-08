package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Map;

/**
 * 제품 등록 명령. 후보검색(네이버)은 별도 out-port — 이 명령은 확정된 등록 내용(수동/선택 후보)을 담는다.
 *
 * @param axes 가격축(variant 정의)·수요축 정의
 * @param variants 가격축 값 조합별 variant (label + priceAxisValues)
 * @param aliases 제품 별칭(매칭 사전 시드)
 */
public record RegisterProductCommand(
		String name,
		String category,
		DemandAxisMode demandAxisMode,
		List<Axis> axes,
		List<Variant> variants,
		List<String> aliases) {

	public record Axis(AxisType axisType, String name, List<String> allowedValues) {
	}

	public record Variant(String label, Map<String, String> priceAxisValues) {
	}
}
