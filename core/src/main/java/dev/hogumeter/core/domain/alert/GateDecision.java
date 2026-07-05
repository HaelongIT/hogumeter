package dev.hogumeter.core.domain.alert;

/** 발송 게이트 판정. SEND_NOW(즉시 발송) / HOLD(방해금지 보류 → 종료 시 플러시). */
public enum GateDecision {
	SEND_NOW,
	HOLD
}
