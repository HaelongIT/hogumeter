package dev.hogumeter.core.application.port.out;

/**
 * 관리 알림 아웃 포트(OBS-03). 딜 알림과 다르다 — 시스템 자신의 건강(파이프라인 연속 실패 등)을 운영자에게
 * 알린다. 1인용이라 운영자 = 사용자이므로 같은 채널로 보내되, "이건 딜이 아니라 시스템 알림"임을 구분한다.
 *
 * <p>실 구현은 텔레그램({@code telegram.enabled=true}), 기본은 스텁(로그만). 실패에 던지지 않는다 —
 * 관리 알림이 못 갔다고 파이프라인을 멈추면 본말이 전도된다.
 */
public interface AdminNotifier {

	void notify(String message);
}
