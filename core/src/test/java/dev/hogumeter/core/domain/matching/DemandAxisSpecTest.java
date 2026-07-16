package dev.hogumeter.core.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Q-66 ① 수요축 값 판별(순수). 확정본 §41: 분리 시 <b>글에서 축 값을 판별</b>하고, 판별 불가면 값 미상이다.
 * 미상은 기준가 표본에서 빠지고 사람이 분류한다 — 그러니 <b>지어내지 않는 것</b>이 이 클래스의 일이다.
 */
class DemandAxisSpecTest {

	private final DemandAxisSpec colors = DemandAxisSpec.of("색상", List.of("블랙", "화이트", "스페이스 그레이"));

	private String normalize(String title) {
		return TitleNormalizer.joined(title);
	}

	@Test
	void findsTheValueInTheTitleAndReturnsTheOriginalSpelling() {
		assertThat(colors.valueIn(normalize("아이폰 17 256기가 블랙 특가"))).isEqualTo("블랙");
	}

	/** 정규화 뒤에 대조하므로 띄어쓰기가 달라도 찾는다 — variant 축값 대조와 같은 수법이다. */
	@Test
	void spacingDoesNotHideTheValue() {
		assertThat(colors.valueIn(normalize("아이폰 17 스페이스그레이 특가"))).isEqualTo("스페이스 그레이");
	}

	@Test
	@DisplayName("값이 안 보이면 미상 — 기본값을 고르지 않는다")
	void missingValueIsUnknown() {
		assertThat(colors.valueIn(normalize("아이폰 17 256기가 특가"))).isNull();
	}

	/**
	 * <b>가장 중요한 케이스.</b> "블랙/화이트 재고"는 어느 색을 산 딜인지 알려주지 않는다. 하나를 골라
	 * 담으면 그 분포가 조용히 오염된다 — 모르면 미상이어야 사람이 분류한다(§41).
	 */
	@Test
	@DisplayName("값이 둘 이상 보이면 미상 — 하나를 골라 지어내지 않는다")
	void ambiguousValueIsUnknown() {
		assertThat(colors.valueIn(normalize("아이폰 17 블랙 화이트 재고 있음"))).isNull();
	}

	@Test
	void unknownValuesInTheTitleDoNotMatch() {
		assertThat(colors.valueIn(normalize("아이폰 17 레드 특가"))).isNull();
	}
}
