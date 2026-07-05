package dev.hogumeter.core.application.port.out;

/**
 * 알림 발송 아웃 포트(AL-05). 실제 구현은 텔레그램 어댑터(정지 조건 — 봇 토큰 필요, 실전송).
 * 도메인/유스케이스는 이 포트만 의존하고, 테스트는 fake로 "무엇을 보냈나"를 검증한다.
 */
public interface AlertSender {

	void send(AlertMessage message);
}
