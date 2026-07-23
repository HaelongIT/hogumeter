package dev.hogumeter.core.application.port.out;

/**
 * 중고 매물 알림의 재료(USED-03, docs/used/04 AC-7·8·9). 신품 딜 알림({@link AlertMessage})과 <b>부류가
 * 다르다</b> — 기준가·강도·게이트가 없고 기준은 사용자가 등록한 조건검색(필수 키워드·목표가)이다.
 * 한 타입에 합치면 어느 쪽도 아닌 필드가 절반씩 null이 된다.
 *
 * @param kind 무엇이 일어났는가(신규·가격하락·판매완료 추정)
 * @param productName 제품 이름. 못 찾으면 null — 어댑터가 "대상 미상"으로 그린다(지어내지 않는다)
 * @param title 매물 제목(as-posted)
 * @param price 현재 가격
 * @param previousPrice 가격하락 알림에서만 채운다. 그 외 null
 * @param url 원문 링크. NULL 가능(V13 이전 관측·파서가 URL을 못 만든 경우) — 어댑터가 링크를 생략한다
 */
public record UsedAlertMessage(UsedAlertKind kind, String productName, String title, long price,
		Long previousPrice, String url) {

	public enum UsedAlertKind {

		/** AC-7 신규 매물이 3계층 필터·목표가를 통과했다. */
		NEW,

		/** AC-8 승격된 매물의 가격이 내렸다. */
		PRICE_DROP,

		/** AC-9 승격된 매물이 목록에서 사라졌다 = 판매완료 <b>추정</b>(Q-44 미실측이라 단정하지 않는다). */
		SOLD_OUT
	}
}
