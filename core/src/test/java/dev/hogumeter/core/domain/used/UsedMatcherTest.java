package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** USED-01 3계층 필터 인수조건(docs/used/04 AC-1~6) — 순수, IO 0. */
class UsedMatcherTest {

	private static UsedSearchSpec spec(List<String> required, List<BonusGroup> bonus, List<String> exclude) {
		return new UsedSearchSpec(required, bonus, exclude);
	}

	// AC-1. required는 AND — 하나라도 빠지면 후보 아님
	@Test
	void requiredIsAnd() {
		UsedSearchSpec s = spec(List.of("아이폰17", "256"), List.of(), List.of());

		assertThat(UsedMatcher.evaluate("아이폰17 128 S급", s).candidate()).isFalse();
		assertThat(UsedMatcher.evaluate("아이폰17 256 블랙", s).candidate()).isTrue();
	}

	// AC-2. SORT — 없어도 알림, 있으면 배지. 알림 조건엔 기여하지 않는다
	@Test
	void sortBadgesButDoesNotGateAlert() {
		UsedSearchSpec s = spec(List.of("아이폰17"),
				List.of(new BonusGroup(List.of("S급", "에스급", "민트"), BonusMode.SORT)), List.of());

		UsedMatchResult withBadge = UsedMatcher.evaluate("아이폰17 256 S급", s);
		UsedMatchResult noBadge = UsedMatcher.evaluate("아이폰17 256 일반", s);

		assertThat(withBadge.hasSortBadge()).isTrue();
		assertThat(noBadge.hasSortBadge()).isFalse();
		assertThat(withBadge.alertEligible()).isTrue(); // 둘 다 알림 후보
		assertThat(noBadge.alertEligible()).isTrue(); // SORT는 알림 게이트가 아님
	}

	// AC-3. TRIGGER — 그룹 중 1개 이상 있어야 알림
	@Test
	void triggerGatesAlert() {
		UsedSearchSpec s = spec(List.of("아이폰17"),
				List.of(new BonusGroup(List.of("미개봉", "새제품"), BonusMode.TRIGGER)), List.of());

		assertThat(UsedMatcher.evaluate("아이폰17 256 미개봉", s).alertEligible()).isTrue();
		UsedMatchResult noTrigger = UsedMatcher.evaluate("아이폰17 256 S급", s);
		assertThat(noTrigger.candidate()).isTrue(); // required는 통과하나
		assertThat(noTrigger.alertEligible()).isFalse(); // 트리거 없어 알림 안 함
	}

	// AC-4. exclude는 NOT — 히트하면 즉시 탈락(required·bonus보다 우선)
	@Test
	void excludeWinsOverRequired() {
		UsedSearchSpec s = spec(List.of("아이폰17", "256"), List.of(),
				List.of("부품용", "액정파손", "침수", "삽니다", "매입"));

		assertThat(UsedMatcher.evaluate("아이폰17 256 액정파손 부품용", s).candidate()).isFalse();
		assertThat(UsedMatcher.evaluate("아이폰17 256 정상", s).candidate()).isTrue();
	}

	// AC-5. 동의어는 그룹 내 나열 — 내장 사전 없음(그룹 OR)
	@Test
	void synonymsWithinGroupAreEquivalent() {
		UsedSearchSpec s = spec(List.of("아이폰17"),
				List.of(new BonusGroup(List.of("S급", "에스급", "민트"), BonusMode.SORT)), List.of());

		assertThat(UsedMatcher.evaluate("아이폰17 256 민트", s).hasSortBadge()).isTrue();
		assertThat(UsedMatcher.evaluate("아이폰17 256 에스급", s).hasSortBadge()).isTrue();
	}

	// AC-6. 정규화 — 대소문자·띄어쓰기 무관
	@Test
	void normalizationIgnoresCaseAndSpacing() {
		UsedSearchSpec s = spec(List.of("iPhone17"), List.of(), List.of());

		assertThat(UsedMatcher.evaluate("iphone 17 256", s).candidate()).isTrue();
		assertThat(UsedMatcher.evaluate("IPHONE17 256", s).candidate()).isTrue();
	}

	// 다중 TRIGGER 그룹 = 그룹 간 AND (잠정 결정, required와 대칭)
	@Test
	void multipleTriggerGroupsAreAnded() {
		UsedSearchSpec s = spec(List.of("아이폰17"), List.of(
				new BonusGroup(List.of("미개봉"), BonusMode.TRIGGER),
				new BonusGroup(List.of("정품"), BonusMode.TRIGGER)), List.of());

		assertThat(UsedMatcher.evaluate("아이폰17 미개봉 정품", s).alertEligible()).isTrue();
		assertThat(UsedMatcher.evaluate("아이폰17 미개봉", s).alertEligible()).isFalse(); // 한 그룹만 충족
	}
}
