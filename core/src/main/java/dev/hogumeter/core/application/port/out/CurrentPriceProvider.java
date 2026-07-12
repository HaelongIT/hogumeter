package dev.hogumeter.core.application.port.out;

/**
 * 현재가 조회 아웃 포트(AL-06, BM-06 currentPrice). 실 구현은 네이버 쇼핑 어댑터(정지 조건 — 키 미발급).
 * 키 발급 전에는 스텁이 <b>미확립(null)</b>을 반환한다 — "값 없음"을 sentinel(0)로 표현하지 않는다(Q-53).
 * 0을 쓰면 갭이 {@code 0 − 기준가} = −100%가 되어 "지금 100% 싸다"는 정상 응답 모양의 거짓말이 된다.
 * 도메인은 현재가를 입력으로만 받고, null이면 갭·잭팟을 계산하지 않는다.
 */
public interface CurrentPriceProvider {

	Long currentPriceFor(long variantId);
}
