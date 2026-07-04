package dev.hogumeter.core.domain.review;

/**
 * 사람 판단 대기 큐 항목 유형(docs/02, V1__init.sql review_queue_item.type).
 * UNCLASSIFIED(미상·애매 매칭 — BM-03) / OUTLIER_LOWER(하향 이상치 — BM-05) / KEYWORD_SUGGEST(사후학습 — BM-07).
 * "판단은 사람"(절대원칙 2): BM은 큐 항목을 만들 뿐, 자동 확정하지 않는다.
 */
public enum ReviewQueueType {
	UNCLASSIFIED,
	OUTLIER_LOWER,
	KEYWORD_SUGGEST
}
