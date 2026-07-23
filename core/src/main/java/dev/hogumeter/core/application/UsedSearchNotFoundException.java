package dev.hogumeter.core.application;

/** 평가 대상 조건검색(used_search)이 없다(USED-04). */
public class UsedSearchNotFoundException extends RuntimeException {

	public static final String CODE = "USED_SEARCH_NOT_FOUND";

	public UsedSearchNotFoundException(long usedSearchId) {
		super("중고 조건검색을 찾을 수 없습니다: #" + usedSearchId);
	}
}
