package dev.hogumeter.core.domain.matching;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 매칭용 수요축 명세(Q-66 ①) — 축 이름 + 허용값들. 확정본 §41: <b>분리(SPLIT) 시 글에서 축 값을 판별</b>하고,
 * 판별하지 못한 딜은 "값 미상" 버킷으로 간다.
 *
 * <p>판별은 {@link VariantSpec}의 축값 대조와 같은 수법이다 — 정규화한 제목에 허용값(정규화)이 들어 있는가.
 * 다만 저장·표시는 <b>원값</b>("블랙")이어야 하므로 정규화값→원값 매핑을 함께 지닌다.
 *
 * <p><b>정확히 하나일 때만 확정한다.</b> 제목에 허용값이 둘 이상 보이면("블랙/화이트 재고") 어느 것을 산
 * 딜인지 알 수 없다 — 지어내지 않고 미상으로 둔다(절대 원칙 6). 그게 §41의 "판별 불가"다.
 */
public record DemandAxisSpec(String name, Map<String, String> valuesByNormalized) {

	public DemandAxisSpec {
		valuesByNormalized = Map.copyOf(valuesByNormalized);
	}

	/** 원값 목록으로 만든다 — 정규화는 여기서 한 번만 한다(호출자가 각자 하면 규칙이 갈린다). */
	public static DemandAxisSpec of(String name, List<String> allowedValues) {
		Map<String, String> byNormalized = new LinkedHashMap<>();
		for (String value : allowedValues) {
			String normalized = TitleNormalizer.joined(value);
			if (!normalized.isEmpty()) {
				byNormalized.put(normalized, value);
			}
		}
		return new DemandAxisSpec(name, byNormalized);
	}

	/**
	 * 정규화된 제목에서 이 축의 값을 판별한다.
	 *
	 * @return 정확히 하나 보이면 그 <b>원값</b>, 아니면 {@code null}(= 값 미상). 없어도 미상이고
	 *     둘 이상 보여도 미상이다 — 모르는 것을 아는 척하지 않는다.
	 */
	public String valueIn(String normalizedTitle) {
		String found = null;
		for (Map.Entry<String, String> candidate : valuesByNormalized.entrySet()) {
			if (normalizedTitle.contains(candidate.getKey())) {
				if (found != null) {
					return null; // 둘 이상 — 어느 것인지 모른다
				}
				found = candidate.getValue();
			}
		}
		return found;
	}
}
