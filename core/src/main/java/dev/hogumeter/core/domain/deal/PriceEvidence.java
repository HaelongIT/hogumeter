package dev.hogumeter.core.domain.deal;

import java.time.Instant;

/**
 * 딜에 링크된 원문 하나가 말하는 가격. {@code raw_deal_post}의 관측이지 딜의 상태가 아니다.
 *
 * @param price 그 원문의 실결제가(배송비 포함, as-posted)
 * @param capturedAt 그 값을 본 시각(발생 시각이 아니라 관측 시각 — 무엇이 "지금"인지 정한다)
 * @param active 아직 살 수 있는가. 품절·삭제된 원문의 가격은 "지금"이 될 수 없다.
 */
public record PriceEvidence(long price, Instant capturedAt, boolean active) {
}
