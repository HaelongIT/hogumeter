package dev.hogumeter.core.domain.used;

/**
 * USED-04 AC-12 입력 3단 폴백. <b>처음부터 한 추상으로</b> 받는다 — 어느 경로든 같은 구조화 필드로
 * 수렴하므로 하류(가격 맥락·위험 신호)는 입력 종류를 몰라도 된다.
 */
public enum EvaluationKind {

	/** 원문 링크. 실 fetch는 정지조건이라 v1은 <b>이미 폴링한 스냅샷</b>에서만 찾는다(docs/91 Q-76). */
	URL,

	/** 붙여넣은 본문. 규칙 추출기가 제목·가격·링크를 읽는다. */
	TEXT,

	/** 사용자가 직접 채운 필드. 추출 실패의 마지막 폴백이다. */
	MANUAL
}
