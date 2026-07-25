package dev.hogumeter.core.application;

/** PRI 우선순위 순번은 유일해야 한다(docs/19 "유일 순번"). 에러코드 PRI_DUPLICATE_RANK로 409 매핑. */
public class DuplicatePriorityRankException extends RuntimeException {

	public static final String CODE = "PRI_DUPLICATE_RANK";

	public DuplicatePriorityRankException(int rank) {
		super("priority rank already in use: " + rank);
	}
}
