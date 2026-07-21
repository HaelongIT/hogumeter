package dev.hogumeter.core.application.port.out;

/**
 * 알림 발송 아웃 포트(AL-05). 실제 구현은 텔레그램 어댑터(정지 조건 — 봇 토큰 필요, 실전송).
 * 도메인/유스케이스는 이 포트만 의존하고, 테스트는 fake로 "무엇을 보냈나"를 검증한다.
 */
public interface AlertSender {

	void send(AlertMessage message);

	/**
	 * 이 발송이 <b>실제로 사용자에게 닿는가</b>. 스텁은 로그만 남기므로 false — 화면이 "목표가를 설정하면
	 * 알림이 온다"고 <b>과대약속</b>하지 않도록(절대 원칙 6), REST가 이 값을 내 web이 "지금은 로그만"이라 밝힌다.
	 * 실 어댑터(텔레그램)만 true. 활성 빈이 스스로 보고하므로 정본이 하나다({@code telegram.enabled} 사본 아님).
	 */
	default boolean delivers() {
		return false;
	}
}
