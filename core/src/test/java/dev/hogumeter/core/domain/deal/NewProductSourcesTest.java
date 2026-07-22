package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 신품 소스 허용집합(BM-02) — 차단 목록이 아니라 허용 목록이라는 사실을 잠근다. */
class NewProductSourcesTest {

	@ParameterizedTest
	@ValueSource(strings = { "ppomppu", "ruliweb", "fmkorea" })
	@DisplayName("신품 핫딜 게시판은 통과한다 — 오차단은 딜을 통째로 잃는다")
	void newProductBoardsAreAccepted(String site) {
		assertThat(NewProductSources.acceptsAsNewProduct(site)).isTrue();
	}

	@Test
	@DisplayName("중고 마켓은 신품으로 해석하지 않는다 — 기준가 오염의 직접 경로다")
	void usedMarketplaceIsRejected() {
		assertThat(NewProductSources.acceptsAsNewProduct("bunjang")).isFalse();
	}

	/**
	 * 핵심 계약. 차단 목록이었다면 "모르는 이름"은 통과했을 것이다 — 새로 생긴 중고 사이트가 조용히
	 * 신품 기준가로 들어가는 길이 바로 그것이다.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "junggonara", "danggeun", "coupang", "새로운사이트", "PPOMPPU-clone" })
	@DisplayName("모르는 소스는 실패해도 안전한 쪽으로 떨어진다")
	void unknownSourcesAreRejected(String site) {
		assertThat(NewProductSources.acceptsAsNewProduct(site)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "PPOMPPU", " ppomppu ", "Ppomppu" })
	@DisplayName("표기 흔들림(대소문자·공백)으로 멀쩡한 게시판을 오차단하지 않는다")
	void acceptedSitesSurviveCaseAndWhitespace(String site) {
		assertThat(NewProductSources.acceptsAsNewProduct(site)).isTrue();
	}

	@Test
	@DisplayName("null 소스에 터지지 않는다")
	void nullIsRejectedNotThrown() {
		assertThat(NewProductSources.acceptsAsNewProduct(null)).isFalse();
	}
}
