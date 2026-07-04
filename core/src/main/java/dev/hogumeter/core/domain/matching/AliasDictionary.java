package dev.hogumeter.core.domain.matching;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 별칭 사전(순수 값) — 정규화·공백제거된 표현 → productId. 제목이 별칭을 substring으로 포함하면 제품 확정.
 * 사람 확정 시 표현이 자동 축적된다(BM-03 AC-4). V1__init.sql alias_dictionary에 대응(전역/제품별).
 */
public record AliasDictionary(Map<String, Long> aliases) {

	public AliasDictionary {
		aliases = Map.copyOf(aliases);
	}

	public static AliasDictionary of(Map<String, Long> aliases) {
		return new AliasDictionary(aliases);
	}

	/** joinedTitle이 어떤 별칭을 포함하면 그 productId. */
	public Optional<Long> match(String joinedTitle) {
		return aliases.entrySet().stream()
				.filter(e -> joinedTitle.contains(e.getKey()))
				.map(Map.Entry::getValue)
				.findFirst();
	}

	/** 표현을 productId로 학습한 새 사전(불변). */
	public AliasDictionary learn(String alias, long productId) {
		Map<String, Long> next = new HashMap<>(aliases);
		next.put(alias, productId);
		return new AliasDictionary(next);
	}
}
