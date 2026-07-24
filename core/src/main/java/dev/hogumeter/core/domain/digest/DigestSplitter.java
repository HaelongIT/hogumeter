package dev.hogumeter.core.domain.digest;

import java.util.ArrayList;
import java.util.List;

/**
 * DIG-02 분할 발송(순수) — "절단 금지·연번". 본문이 {@code maxLength}를 넘으면 <b>줄 경계에서만</b> 나눈다
 * (단어·문장을 자르지 않는다). 안 나뉘면 원문 그대로(연번 불필요) — 나뉘면 각 조각 앞에 "(i/N) "를 붙인다.
 *
 * <p><b>연번을 붙이는 순서가 핵심이다</b>: 조각 수(N)를 알아야 "(i/N)"을 쓸 수 있는데, 그 접두어 자체가
 * 조각 길이를 늘린다. 그래서 먼저 접두어 예산({@link #PREFIX_BUDGET})만큼 보수적으로 줄인 한도로 줄
 * 단위 배분을 끝낸 뒤, 확정된 N으로 접두어를 붙인다(2단계 — 순환 의존을 끊는다).
 *
 * <p>한 줄 자체가 유효 한도를 넘으면 그 줄은 그대로 한 조각이 된다 — <b>절단 금지가 길이 제한보다
 * 우선</b>이다(넘치는 한 통을 보내는 것이 문장을 잘라 보내는 것보다 낫다, 정직성).
 */
public final class DigestSplitter {

	/** "(99/99) " 두 자리 조각 수까지 안전한 예산. 그 이상(100조각↑)은 이 1인용 시스템 규모에서 안 일어난다. */
	private static final int PREFIX_BUDGET = 10;

	private DigestSplitter() {
	}

	public static List<String> split(String text, int maxLength) {
		if (text.length() <= maxLength) {
			return List.of(text);
		}
		List<String> parts = packByLines(text, maxLength - PREFIX_BUDGET);
		if (parts.size() <= 1) {
			return parts;
		}
		List<String> numbered = new ArrayList<>(parts.size());
		for (int i = 0; i < parts.size(); i++) {
			numbered.add("(" + (i + 1) + "/" + parts.size() + ") " + parts.get(i));
		}
		return numbered;
	}

	private static List<String> packByLines(String text, int effectiveLimit) {
		List<String> parts = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			String candidate = current.isEmpty() ? line : current + "\n" + line;
			if (candidate.length() > effectiveLimit && !current.isEmpty()) {
				parts.add(current.toString());
				current = new StringBuilder(line);
			}
			else {
				current = new StringBuilder(candidate);
			}
		}
		if (!current.isEmpty() || parts.isEmpty()) {
			parts.add(current.toString());
		}
		return parts;
	}
}
