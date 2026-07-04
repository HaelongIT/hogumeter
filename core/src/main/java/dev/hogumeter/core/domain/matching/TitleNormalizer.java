package dev.hogumeter.core.domain.matching;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * BM-03 v1 탈모델 정규화 — 용량/숫자 표기를 표준화(임베딩·LLM 없이 규칙 기반).
 * 기가→GB, 테라→TB, 숫자 뒤 단독 G/T → GB/TB. 공백은 토큰화·조인 시점에 다룬다.
 */
public final class TitleNormalizer {

	private TitleNormalizer() {
	}

	public static String normalize(String title) {
		String r = title.replace("테라", "TB").replace("기가", "GB");
		r = r.replaceAll("(\\d)G\\b", "$1GB");
		r = r.replaceAll("(\\d)T\\b", "$1TB");
		return r;
	}

	/** 공백 제거한 정규화 문자열 — 별칭 substring 히트 판정용. */
	public static String joined(String title) {
		return normalize(title).replaceAll("\\s+", "");
	}

	/** 정규화 토큰 집합 — 부분 일치(코어 토큰 교집합) 판정용. */
	public static Set<String> tokens(String title) {
		return new LinkedHashSet<>(Arrays.asList(normalize(title).trim().split("\\s+")));
	}
}
