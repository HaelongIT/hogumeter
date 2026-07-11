package dev.hogumeter.core.domain.used;

import dev.hogumeter.core.domain.matching.TitleNormalizer;
import java.util.Locale;

/**
 * USED-01 3계층 필터(순수 도메인, IO 0). 매물 title 문자열을 required(AND)/bonusGroups(OR, 모드별)/
 * exclude(NOT)로 판정한다. 내장 동의어 사전 없음 — 동의어는 그룹 안에 나열된 것만 같다(AC-5).
 *
 * <p>정규화(AC-6): BM 정규화(기가→GB 등)에 공백 제거·소문자화를 더해 대소문자·띄어쓰기 무관 매칭.
 * required·bonus·exclude에 일관 적용. substring 포함으로 히트 판정.
 *
 * <p>다중 TRIGGER 그룹 관계는 <b>그룹 간 AND</b>로 둔다(required와 대칭 — 각 TRIGGER 그룹이 독립 필수
 * 조건). AC-3은 단일 그룹만 규정하므로 이는 잠정(되돌리기 쉬운, 한 함수). 재조정 트리거: 실사용에서
 * "여러 트리거 중 하나라도"가 필요해지면 anyMatch로 전환.
 */
public final class UsedMatcher {

	private UsedMatcher() {
	}

	public static UsedMatchResult evaluate(String title, UsedSearchSpec spec) {
		String norm = normalize(title);
		boolean excluded = spec.exclude().stream().anyMatch(k -> norm.contains(normalize(k)));
		boolean requiredAll = spec.required().stream().allMatch(k -> norm.contains(normalize(k)));
		boolean candidate = requiredAll && !excluded; // exclude가 required보다 우선(AC-4)

		boolean triggerSatisfied = spec.bonusGroups().stream()
				.filter(g -> g.mode() == BonusMode.TRIGGER)
				.allMatch(g -> groupHit(norm, g)); // 그룹 없으면 allMatch=true
		boolean hasSortBadge = spec.bonusGroups().stream()
				.filter(g -> g.mode() == BonusMode.SORT)
				.anyMatch(g -> groupHit(norm, g));

		return new UsedMatchResult(candidate, triggerSatisfied, hasSortBadge);
	}

	/** 그룹 내 OR — 키워드 하나라도 매칭되면 그룹 히트(AC-5 동의어). */
	private static boolean groupHit(String normalizedTitle, BonusGroup group) {
		return group.keywords().stream().anyMatch(k -> normalizedTitle.contains(normalize(k)));
	}

	private static String normalize(String s) {
		return TitleNormalizer.joined(s).toLowerCase(Locale.ROOT);
	}
}
