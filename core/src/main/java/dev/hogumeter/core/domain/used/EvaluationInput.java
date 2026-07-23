package dev.hogumeter.core.domain.used;

/**
 * USED-04 평가 요청의 원재료(순수). 종류마다 채워지는 필드가 다르지만 <b>타입은 하나</b>다 —
 * 3단 폴백은 같은 입구를 통과해야 하류가 갈라지지 않는다(AC-12).
 */
public record EvaluationInput(EvaluationKind kind, String text, String title, Long price, String url) {

	public static EvaluationInput text(String text) {
		return new EvaluationInput(EvaluationKind.TEXT, text, null, null, null);
	}

	public static EvaluationInput manual(String title, Long price, String url) {
		return new EvaluationInput(EvaluationKind.MANUAL, null, title, price, url);
	}

	public static EvaluationInput url(String url) {
		return new EvaluationInput(EvaluationKind.URL, null, null, null, url);
	}
}
