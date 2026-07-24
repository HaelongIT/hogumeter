package dev.hogumeter.core.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** DIG-02 분할 발송 순수 계약 — 절단 금지·연번·안 나뉘면 원문 그대로. */
class DigestSplitterTest {

	@Test
	void shortTextIsReturnedAsOnePartWithoutNumbering() {
		List<String> parts = DigestSplitter.split("한 줄짜리 본문", 100);

		assertThat(parts).containsExactly("한 줄짜리 본문");
	}

	@Test
	void longTextIsSplitOnLineBoundariesAndNumbered() {
		String line = "가".repeat(30);
		String text = String.join("\n", List.of(line, line, line, line, line)); // 5줄 * 31(개행 포함) ≈ 155자

		List<String> parts = DigestSplitter.split(text, 60);

		assertThat(parts.size()).isGreaterThan(1);
		assertThat(parts).allSatisfy(p -> assertThat(p).matches("^\\(\\d+/" + parts.size() + "\\) [\\s\\S]*"));
		// 접두어를 떼고 다시 이으면 원문과 같다 — 줄이 사라지거나 잘리지 않았다.
		String rejoined = parts.stream()
				.map(p -> p.replaceFirst("^\\(\\d+/\\d+\\) ", ""))
				.collect(Collectors.joining("\n"));
		assertThat(rejoined).isEqualTo(text);
	}

	@Test
	void noPartExceedsMaxLengthWhenLinesAreShortEnoughToPack() {
		String line = "가나다"; // 짧은 줄
		String text = String.join("\n", java.util.Collections.nCopies(50, line));

		List<String> parts = DigestSplitter.split(text, 60);

		assertThat(parts.size()).isGreaterThan(1);
		assertThat(parts).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(60));
	}

	/** 절단 금지가 길이 제한보다 우선 — 유효 한도를 넘는 한 줄도 안 잘리고 그대로 한 조각이 된다. */
	@Test
	void aSingleLineLongerThanTheLimitIsNeverCutMidLine() {
		String oneHugeLine = "가".repeat(200);

		List<String> parts = DigestSplitter.split(oneHugeLine, 60);

		assertThat(parts).containsExactly(oneHugeLine); // 1조각이라 연번도 안 붙는다
	}
}
