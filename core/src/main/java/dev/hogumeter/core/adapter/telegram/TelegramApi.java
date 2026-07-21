package dev.hogumeter.core.adapter.telegram;

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
}
