package dev.hogumeter.core.domain.digest;

import java.time.Instant;

/**
 * DIG-04 ④ 핀 결말 전이 + 부활 이벤트(docs/18, docs/17). "핀 이력 딜"(WatchItem이 있는 딜, 상태 무관)
 * 에서 이번 창 안에 벌어진 사건 하나. 기각(DROPPED)은 원문이 제외한다 — 결말 3종 중 BOUGHT·MISSED만,
 * 그리고 REVIVED(DN-C1 REOPENED 후속의 핀 이력 딜 한정 부분집합).
 */
public record PinDigestEvent(long dealEventId, PinDigestEventType type, Instant occurredAt) {

	public enum PinDigestEventType {
		BOUGHT, MISSED, REVIVED
	}
}
