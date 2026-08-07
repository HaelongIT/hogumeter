package dev.hogumeter.core.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * BE-03(코드리뷰 20260806) — substring 다중 히트 시 매칭이 비결정적이 되지 않도록, 서로 다른 productId를
 * 가리키는 별칭이 둘 이상 걸리면 확정하지 않는다(미상/후보로 내려보낸다). {@link AliasDictionary}는 내부에
 * {@code Map.copyOf}(순회 순서가 JVM 실행마다 달라짐)를 쓰므로, 확정 여부가 순회 순서에 기대면 안 된다.
 */
class AliasDictionaryTest {

	@Test
	void singleHitConfirms() {
		AliasDictionary dict = AliasDictionary.of(Map.of("아이폰17", 1L));

		assertThat(dict.match("아이폰17 256기가")).contains(1L);
	}

	@Test
	void noHitIsEmpty() {
		AliasDictionary dict = AliasDictionary.of(Map.of("아이폰17", 1L));

		assertThat(dict.match("갤럭시25 256기가")).isEmpty();
	}

	/**
	 * "아이폰17"과 "아이폰17프로"처럼 한 별칭이 다른 별칭의 substring이면, 같은 제목이 서로 다른
	 * productId를 가진 별칭 둘 다에 걸릴 수 있다 — 이 경우 어느 쪽도 확정하지 않는다(정직성 원칙).
	 */
	@Test
	void ambiguousMultiHitAcrossDifferentProductsDoesNotConfirm() {
		AliasDictionary dict = AliasDictionary.of(Map.of("아이폰17", 1L, "아이폰17프로", 2L));

		assertThat(dict.match("아이폰17프로 256기가")).isEmpty();
	}

	/** 다중 히트라도 같은 productId를 가리키면(동의어 등) 모호하지 않다 — 확정한다. */
	@Test
	void multiHitTheSameProductStillConfirms() {
		AliasDictionary dict = AliasDictionary.of(Map.of("아이폰17", 1L, "iPhone17", 1L));

		assertThat(dict.match("아이폰17 iPhone17 256기가")).contains(1L);
	}
}
