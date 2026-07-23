package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * USED-04 AC-12·15 — 입력 3단 폴백의 v1 규칙 추출기(순수). <b>못 읽으면 지어내지 않는다</b>:
 * 가격을 못 구하면 빈 결과를 내고, 그러면 상위가 "수동 입력"을 요청한다(AC-12 ③단).
 */
class RuleBasedListingExtractorTest {

	private final ListingExtractor extractor = new RuleBasedListingExtractor();

	@Test
	@DisplayName("MANUAL은 사용자가 준 값을 그대로 쓴다 — 추출할 것이 없다")
	void manualPassesThrough() {
		EvaluationInput input = EvaluationInput.manual("아이폰 17 256 S급", 850_000L, "https://m.example/1");

		assertThat(extractor.extract(input)).get().satisfies(e -> {
			assertThat(e.title()).isEqualTo("아이폰 17 256 S급");
			assertThat(e.price()).isEqualTo(850_000L);
			assertThat(e.url()).isEqualTo("https://m.example/1");
		});
	}

	@Test
	@DisplayName("TEXT: 첫 줄이 제목, 본문에서 원 단위 가격을 읽는다")
	void textExtractsTitleAndWonPrice() {
		EvaluationInput input = EvaluationInput.text("""
				아이폰 17 프로 256기가 팝니다
				상태 A급이고 잔기스 있어요
				850,000원 직거래만""");

		assertThat(extractor.extract(input)).get().satisfies(e -> {
			assertThat(e.title()).isEqualTo("아이폰 17 프로 256기가 팝니다");
			assertThat(e.price()).isEqualTo(850_000L);
		});
	}

	@Test
	@DisplayName("TEXT: 만원 단위도 읽는다 — 중고 글에서 가장 흔한 표기")
	void textExtractsManWonPrice() {
		assertThat(extractor.extract(EvaluationInput.text("갤럭시 S25 팝니다\n85만원에 드려요"))).get()
				.extracting(ExtractedListing::price).isEqualTo(850_000L);
	}

	@Test
	@DisplayName("TEXT: 가격을 못 찾으면 빈 결과 — 0원으로 흘려보내지 않는다")
	void textWithoutPriceExtractsNothing() {
		assertThat(extractor.extract(EvaluationInput.text("아이폰 팝니다\n가격은 쪽지 주세요"))).isEmpty();
	}

	/**
	 * "모르는 입력 앞에서 침묵하는 기본값이 가장 위험하다" — 용량·모델명의 숫자를 가격으로 읽으면
	 * 조용히 틀린 맥락을 그린다(`RTX 5070`을 5,070원으로 읽던 파서와 같은 함정).
	 */
	@Test
	@DisplayName("TEXT: 단위 없는 숫자는 가격이 아니다 — 모델명·용량을 가격으로 읽지 않는다")
	void bareNumbersAreNotPrices() {
		assertThat(extractor.extract(EvaluationInput.text("RTX 5070 그래픽카드 팝니다\n256 기가 모델"))).isEmpty();
	}

	@Test
	@DisplayName("URL은 이 추출기가 다루지 않는다 — 실 fetch는 정지조건이라 상위가 스냅샷·수동으로 폴백")
	void urlIsNotHandledHere() {
		assertThat(extractor.extract(EvaluationInput.url("https://m.bunjang.co.kr/products/1"))).isEmpty();
	}

	@Test
	@DisplayName("TEXT: 여러 금액이 있으면 가장 큰 것을 매물가로 본다 — 배송비·에누리가 아니라")
	void picksTheLargestAmountAsThePrice() {
		EvaluationInput input = EvaluationInput.text("아이폰 17 팝니다\n택배비 3,000원 별도\n850,000원");

		assertThat(extractor.extract(input)).get().extracting(ExtractedListing::price).isEqualTo(850_000L);
	}

	@Test
	@DisplayName("TEXT: 본문의 링크를 원문 링크로 싣는다 — 사람이 원문으로 갈 수 있어야 한다")
	void textCarriesAnyLinkItFinds() {
		EvaluationInput input = EvaluationInput.text("아이폰 17\n85만원\nhttps://m.bunjang.co.kr/products/7");

		assertThat(extractor.extract(input)).get().extracting(ExtractedListing::url)
				.isEqualTo("https://m.bunjang.co.kr/products/7");
	}

	@Test
	@DisplayName("빈 텍스트는 아무것도 내지 않는다")
	void emptyTextExtractsNothing() {
		assertThat(extractor.extract(EvaluationInput.text("   "))).isEmpty();
		assertThat(extractor.extract(new EvaluationInput(EvaluationKind.TEXT, null, null, null, null))).isEmpty();
	}
}
