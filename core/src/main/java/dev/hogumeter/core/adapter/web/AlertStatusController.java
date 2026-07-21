package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.port.out.AlertSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 발송 상태(AL-05). 화면이 "목표가를 설정하면 알림이 온다"고 과대약속하지 않도록(절대 원칙 6),
 * 지금 알림이 <b>실제로 사용자에게 닿는지</b>(텔레그램 실전송) 아니면 <b>로그로만</b> 남는지(스텁)를 알려준다.
 *
 * <p>판정은 활성 {@link AlertSender} 빈이 스스로 보고한다({@code delivers()}) — {@code telegram.enabled}를
 * 컨트롤러가 따로 읽으면 사본이 생겨 조건과 드리프트한다. 빈이 진실이다.
 */
@RestController
public class AlertStatusController {

	private final AlertSender sender;

	public AlertStatusController(AlertSender sender) {
		this.sender = sender;
	}

	@GetMapping("/api/v1/alerts/status")
	public AlertStatus status() {
		return new AlertStatus(sender.delivers());
	}

	/**
	 * @param delivering true = 실제로 사용자에게 발송된다(텔레그램). false = 로그로만 남는다(스텁) — 화면이
	 *     "지금은 알림이 안 갑니다"를 밝혀야 한다.
	 */
	public record AlertStatus(boolean delivering) {
	}
}
