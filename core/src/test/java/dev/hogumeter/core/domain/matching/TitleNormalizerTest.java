package dev.hogumeter.core.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BM-03 용량/숫자 패턴 정규화 — "1TB"↔"1테라", "256G"↔"256GB"(docs/04 테스트 매트릭스). */
class TitleNormalizerTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"256기가, 256GB", "256G, 256GB", "256GB, 256GB",
			"1테라, 1TB", "1T, 1TB", "1TB, 1TB",
			"512기가, 512GB"
	})
	void normalizesCapacityUnits(String raw, String expected) {
		assertThat(TitleNormalizer.normalize(raw)).isEqualTo(expected);
	}

	@Test
	void keepsSpacesButNormalizesUnitsInSentence() {
		assertThat(TitleNormalizer.normalize("아이폰 17 256기가 자급제")).isEqualTo("아이폰 17 256GB 자급제");
	}

	@Test
	void joinedRemovesWhitespace() {
		assertThat(TitleNormalizer.joined("아이폰 17 256기가")).isEqualTo("아이폰17256GB");
	}
}
