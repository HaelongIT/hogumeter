package dev.hogumeter.core.domain.review;

import java.util.Map;

/**
 * 사람 승격/기각 대기 큐 항목(순수 값). BM-03·05·07이 생성하고, 처리 UI·채널은 기능3(알림)이 소유.
 * 큐 영속화·알림 발화는 어댑터/AL 관심사 — 도메인은 항목 값만 만든다.
 *
 * @param payload 유형별 근거 데이터(예: OUTLIER_LOWER = priceFirst·site·sourceUrl)
 */
public record ReviewQueueItem(ReviewQueueType type, Map<String, Object> payload) {

	public ReviewQueueItem {
		payload = Map.copyOf(payload);
	}
}
