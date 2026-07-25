package dev.hogumeter.core.domain.watch;

import java.util.Map;
import java.util.Set;

/**
 * WATCH(docs/17) 핀 결말 상태기계. ACTIVE(관찰 중)에서만 결말로 나간다 — BOUGHT([샀어요], 수동) /
 * MISSED(ENDED 감지, 자동 — "종료됨(미구매)"일 뿐 사기 판정이 아니다) / DROPPED(기각·해제, 둘 다 결과가
 * 같아 상태 하나로 합친다). 결말은 전부 종착이다 — "결말 후 재핀 허용"은 <b>같은 상태를 되돌리는 전이가
 * 아니라</b> 새 WatchItem 행을 만드는 것이다(이력 연결은 그 행의 dealEventId로 잇는다, 상태기계 밖의 일).
 */
public enum PinState {
	ACTIVE,
	BOUGHT,
	MISSED,
	DROPPED;

	private static final Map<PinState, Set<PinState>> ALLOWED = Map.of(
			ACTIVE, Set.of(BOUGHT, MISSED, DROPPED),
			BOUGHT, Set.of(),
			MISSED, Set.of(),
			DROPPED, Set.of());

	public boolean canTransitionTo(PinState target) {
		return ALLOWED.get(this).contains(target);
	}

	public PinState transitionTo(PinState target) {
		if (!canTransitionTo(target)) {
			throw new IllegalPinTransitionException(this, target);
		}
		return target;
	}

	public boolean isTerminal() {
		return this != ACTIVE;
	}
}
