package dev.hogumeter.core.domain.review;

/**
 * 사람 판단 대기 큐 항목 유형(docs/02, review_queue_item.type).
 * UNCLASSIFIED(미상·애매 매칭 — BM-03) / OUTLIER_LOWER(하향 이상치 — BM-05) / KEYWORD_SUGGEST(사후학습 — BM-07)
 * / DEMAND_UNKNOWN(분리 제품인데 제목에서 수요축 값을 판별 못 함 — Q-66 ①, 확정본 §41).
 * "판단은 사람"(절대원칙 2): BM은 큐 항목을 만들 뿐, 자동 확정하지 않는다.
 *
 * <p>DEMAND_UNKNOWN과 UNCLASSIFIED는 다르다: UNCLASSIFIED는 <b>어느 제품·variant인지</b>를 모르고,
 * DEMAND_UNKNOWN은 variant는 확정됐고 <b>어느 수요축 값(색상 등)인지</b>만 모른다.
 */
public enum ReviewQueueType {
	UNCLASSIFIED,
	OUTLIER_LOWER,
	KEYWORD_SUGGEST,
	DEMAND_UNKNOWN
}
