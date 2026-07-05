package dev.hogumeter.core.application;

/** 알림 디스패치 결과. SENT(발송) / HELD(방해금지 보류) / NO_ALERT(트리거 미충족). */
public enum DispatchOutcome {
	SENT,
	HELD,
	NO_ALERT
}
