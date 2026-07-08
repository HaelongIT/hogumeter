package dev.hogumeter.core.application.port.out;

/**
 * 현재가 조회 아웃 포트(AL-06, BM-06 currentPrice). 실 구현은 네이버 쇼핑 어댑터(정지 조건 — 키 미발급).
 * 키 발급 전에는 스텁이 미확립(0)을 반환한다. 도메인은 현재가를 입력으로만 받는다.
 */
public interface CurrentPriceProvider {

	long currentPriceFor(long variantId);
}
