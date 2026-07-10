package dev.hogumeter.core.domain.deal;

/**
 * {@code deal_event.applied_conditions}에 실리는 <b>기계용 표식</b>. 나머지 원소는 사람이 읽는 설명이다.
 *
 * <p><b>이 문자열의 소유자는 collector다</b> — 정본은 {@code collector/src/collector/pipeline/price.py}의
 * {@code SHIPPING_UNKNOWN}이고, 여기 있는 건 그 값을 DB 너머에서 읽기 위한 사본이다. 사본은 드리프트하고,
 * 드리프트한 사본은 GREEN인 채로 거짓말한다(0건을 세면서 "오염 없음"이라고 말한다). 그래서
 * {@code scripts/check-tag-contract.sh}가 두 리터럴이 같은지 CI에서 강제한다.
 *
 * <p><b>표식을 바꾸려면 마이그레이션이 필요하다.</b> 게이트는 두 리터럴만 본다 — 이미 DB에 쌓인
 * {@code deal_event.applied_conditions}의 옛 표식은 새 이름으로 검색되지 않아 <b>조용히 안 세어진다.</b>
 */
public final class DealTags {

	/**
	 * 배송비를 모른 채 0을 더한 딜. 저장된 가격은 실결제가가 아니라 <b>하한</b>이다(BM-02).
	 *
	 * <p>뽐뿌 {@code 유배}(유료배송 금액미상), 펨코 {@code 조건부무료배송:*}(멤버십·장바구니 임계).
	 * {@code 카할}(카드할인)은 <b>여기 들지 않는다</b> — 확정본 AC-2가 허용한 as-posted 값이다.
	 *
	 * <p>이 표식이 붙은 딜은 기준가를 실제보다 아래로 끈다(좋은 딜을 놓친다, 절대 원칙 3).
	 * 표본에서 하한으로 취급하는 일은 아직 미구현 — docs/91 Q-46 재개 트리거 ②.
	 */
	public static final String SHIPPING_UNKNOWN = "배송비미상";

	private DealTags() {
	}
}
