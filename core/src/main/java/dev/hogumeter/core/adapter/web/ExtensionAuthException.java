package dev.hogumeter.core.adapter.web;

/** CMP-02 SEC-04 — 확장 ingest 토큰이 없거나 틀렸다. */
public class ExtensionAuthException extends RuntimeException {

	public static final String CODE = "EXTENSION_AUTH_FAILED";

	public ExtensionAuthException() {
		super("확장 ingest 토큰이 없거나 올바르지 않습니다");
	}
}
