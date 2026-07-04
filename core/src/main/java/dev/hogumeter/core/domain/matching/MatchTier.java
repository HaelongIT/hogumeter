package dev.hogumeter.core.domain.matching;

/**
 * 매칭 3계층 + 미상(BM-03). CONFIRMED(별칭 히트·축값 배정) / CANDIDATE(부분 일치, 재현율 우선) /
 * UNKNOWN(제품 확실·축값 판별 불가 = 미상 버킷) / REJECTED(무관).
 */
public enum MatchTier {
	CONFIRMED,
	CANDIDATE,
	UNKNOWN,
	REJECTED
}
