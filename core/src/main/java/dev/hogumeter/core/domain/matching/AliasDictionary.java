package dev.hogumeter.core.domain.matching;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

	/**
	 * joinedTitle이 어떤 별칭을 포함하면 그 productId. 서로 다른 productId를 가리키는 별칭이 <b>둘 이상</b>
	 * 히트하면(예: "아이폰17"·"아이폰17프로" — 한쪽이 다른 쪽의 substring) 어느 것도 확정하지 않고
	 * 미상({@link Optional#empty()})을 반환한다(코드리뷰 BE-01·03). {@code aliases}는 컴팩트 생성자에서
	 * {@code Map.copyOf}로 감싸져 순회 순서가 JVM 실행마다 달라지므로(JDK {@code MapN}, SALT32L),
	 * 여기서 {@code findFirst()}로 하나를 고르면 재기동마다 다른 제품이 확정될 수 있었다 — 그래서 순서에
	 * 기대지 않고 <b>히트한 productId 전부</b>를 모아 판정한다. 같은 productId로만 여러 번 히트하는 것은
	 * (동의어 등) 모호하지 않다.
	 */
	public Optional<Long> match(String joinedTitle) {
		Set<Long> hits = aliases.entrySet().stream()
				.filter(e -> joinedTitle.contains(e.getKey()))
				.map(Map.Entry::getValue)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return hits.size() == 1 ? Optional.of(hits.iterator().next()) : Optional.empty();
	}

	/** 표현을 productId로 학습한 새 사전(불변). */
	public AliasDictionary learn(String alias, long productId) {
		Map<String, Long> next = new HashMap<>(aliases);
		next.put(alias, productId);
		return new AliasDictionary(next);
	}
}
