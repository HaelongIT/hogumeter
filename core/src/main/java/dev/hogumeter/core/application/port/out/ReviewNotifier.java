package dev.hogumeter.core.application.port.out;

/**
 * 미상 큐 항목이 새로 생겼을 때 사람에게 알리는 아웃 포트(Q-15 인바운드 버튼의 아웃바운드 짝). 텔레그램이면
 * 인라인 [승격][기각] 버튼과 함께 보내 그 자리에서 분류하게 한다. 텔레그램 미설정이면 no-op — 항목은 web
 * 미상 큐로 본다(로그도 안 남긴다: web이 이미 그 창구다).
 *
 * <p>딜 알림({@link AlertSender})·관리 알림({@link AdminNotifier})과 다르다 — 이건 <b>버튼(액션)</b>을 실어
 * 사용자가 응답하게 한다. 새로 생긴 항목에만 보낸다(재적재/반복은 아님) — dedup는 생산자가 한다.
 */
public interface ReviewNotifier {

	/**
	 * @param reviewItemId 버튼 callback_data에 실릴 항목 id("promote:{id}").
	 * @param summary 사람이 읽을 한 줄("🔍 이상치 의심 …").
	 * @param promotable 승격 가능(OUTLIER_LOWER)이면 [승격][기각], 아니면 [기각]만 — core가 400으로 막는 승격을 버튼으로도 안 그린다.
	 */
	void notify(long reviewItemId, String summary, boolean promotable);
}
