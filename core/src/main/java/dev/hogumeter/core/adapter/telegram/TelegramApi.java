package dev.hogumeter.core.adapter.telegram;

import java.util.List;

/**
 * 텔레그램 Bot API 전송 이음새(seam). 실 구현({@link HttpTelegramApi})은 HTTP를 치고, 테스트는 fake로
 * "무엇을 어느 chat에 보냈나 · 어떤 상태가 오면 어떻게 처리하나"를 실 네트워크 없이 검증한다.
 */
public interface TelegramApi {

	/**
	 * sendMessage 호출.
	 *
	 * @return HTTP 상태 코드(200 성공, 403/429 거절·차단, 5xx 일시장애 — 분류는 {@link TelegramAlertSender}).
	 * @throws RuntimeException 네트워크 자체가 닿지 못했을 때(일시장애로 다룬다).
	 */
	int sendMessage(String chatId, String text);

	/**
	 * 인라인 버튼과 함께 보낸다(Q-15 미상 큐 승격·기각). 기본 구현은 버튼을 무시하고 본문만 보낸다 — 실
	 * 구현({@link HttpTelegramApi})만 {@code reply_markup}으로 버튼을 싣는다. 그래서 발송 fake는 안 깨진다.
	 */
	default int sendMessage(String chatId, String text, List<Button> buttons) {
		return sendMessage(chatId, text);
	}

	/** 인라인 버튼 한 개. {@code callbackData}는 누르면 봇이 받는 값("promote:123") — 라우터가 파싱한다. */
	record Button(String text, String callbackData) {
	}
}
