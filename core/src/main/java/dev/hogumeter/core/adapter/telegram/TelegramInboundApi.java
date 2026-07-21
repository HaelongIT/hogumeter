package dev.hogumeter.core.adapter.telegram;

import java.util.List;

/**
 * 텔레그램 인바운드(버튼 콜백 수신) seam. 아웃바운드({@link TelegramApi})와 분리해 기존 발송 fake가
 * 안 깨지게 한다. 실 구현은 {@link HttpTelegramApi}(getUpdates·answerCallbackQuery), 테스트는 fake로
 * "무엇을 받고 무엇으로 답하나"를 실 네트워크 없이 검증한다.
 */
public interface TelegramInboundApi {

	/**
	 * {@code offset} 이상의 콜백 업데이트를 가져온다(그 미만은 이미 처리·확인됨). 콜백이 아닌 업데이트는
	 * 걸러 반환하지 않는다 — 이 봇은 인라인 버튼만 다룬다.
	 */
	List<CallbackUpdate> getUpdates(long offset);

	/** 버튼 누른 사람에게 짧은 확인(토스트)을 답한다 — 텔레그램의 로딩 스피너를 끈다. */
	void answerCallbackQuery(String callbackQueryId, String text);

	/**
	 * @param updateId 확인(offset) 계산용 — 처리하면 {@code updateId+1}부터 다시 폴한다.
	 * @param fromChatId 누른 사람(SEC-03 화이트리스트 대조).
	 * @param data {@code callback_data}("promote:123").
	 * @param callbackQueryId answerCallbackQuery 대상.
	 */
	record CallbackUpdate(long updateId, long fromChatId, String data, String callbackQueryId) {
	}
}
