package dev.hogumeter.core.domain.watch;

/** WATCH(docs/17) 핀 결말 상태기계의 비허용 전이(대개 "이미 결말난 핀을 또 결말내려는" REST 호출). */
public class IllegalPinTransitionException extends RuntimeException {

	public static final String CODE = "WATCH_ILLEGAL_PIN_TRANSITION";

	public IllegalPinTransitionException(PinState from, PinState to) {
		super("illegal pin transition: " + from + " -> " + to);
	}
}
