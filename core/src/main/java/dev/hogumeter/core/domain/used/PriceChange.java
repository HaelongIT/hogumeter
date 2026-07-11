package dev.hogumeter.core.domain.used;

/** 두 스냅샷 사이 같은 매물(listingId)의 가격 변동(USED-02 AC-8). 후속 알림 여부는 promoted 필터가 별도 판정. */
public record PriceChange(String listingId, long previousPrice, long currentPrice) {
}
