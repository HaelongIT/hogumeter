package dev.hogumeter.core.domain.used;

/**
 * 입력 3단이 수렴하는 구조화 필드(USED-04 AC-12). 여기까지 오면 어느 경로로 들어왔는지는 잊는다.
 *
 * @param title 매물 제목(as-posted)
 * @param price 가격. <b>여기까지 왔다면 값이 있다</b> — 못 구했으면 추출 자체가 실패한다(0을 흘리지 않는다)
 * @param url 원문 링크. 없을 수 있다(붙여넣은 본문에 링크가 없으면 null)
 */
public record ExtractedListing(String title, long price, String url) {
}
