package dev.hogumeter.core.adapter.web;

/** CMP-02 SEC-04 — 확장 ingest 레이트리밋 초과(docs/91 Q-78 잠정 수치). */
public class RateLimitExceededException extends RuntimeException {

	public static final String CODE = "RATE_LIMIT_EXCEEDED";

	public RateLimitExceededException() {
		super("요청이 너무 잦습니다. 잠시 후 다시 시도하세요");
	}
}
