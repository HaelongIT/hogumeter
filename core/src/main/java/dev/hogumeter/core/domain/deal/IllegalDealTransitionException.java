package dev.hogumeter.core.domain.deal;

/** 허용되지 않은 DealEvent 상태 전이 시도(BM-04 AC-6). */
public class IllegalDealTransitionException extends RuntimeException {

	public IllegalDealTransitionException(DealStatus from, DealStatus to) {
		super("illegal deal transition: " + from + " → " + to);
	}
}
