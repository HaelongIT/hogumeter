package dev.hogumeter.core.domain.used;

/**
 * 한 폴링 사이클에서 관측된 매물 한 건(USED-02 목록 diff 입력). {@code listingId}는 플랫폼 매물 ID =
 * <b>자연키</b>(끌올 dedupe·소실 판정의 기준). 목록 페이지에서 얻는 최소 필드만 — 상세는 승격 시 1회 fetch.
 */
public record ObservedListing(String listingId, long price) {
}
