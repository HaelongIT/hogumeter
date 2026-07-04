package dev.hogumeter.core.domain.deal;

import java.time.Instant;

/**
 * 병합된 하나의 딜(순수 값 타입). BM-06 기준가 산출의 입력.
 * 대표가 = priceFirst(분포 입력은 일관되게 price_first — docs/benchmark/01 line 26).
 * 전체 스키마는 V1__init.sql deal_event; 여기서는 BM-06이 소비하는 필드만 표현하고
 * 병합·상태기계(BM-04)·이상치 판정(BM-05)이 이 타입을 확장 재사용한다.
 */
public record DealEvent(
		long variantId,
		long priceFirst,
		boolean crossVerified,
		Origin origin,
		OutlierFlag outlierFlag,
		Instant firstSeen,
		String site,
		String sourceUrl) {
}
