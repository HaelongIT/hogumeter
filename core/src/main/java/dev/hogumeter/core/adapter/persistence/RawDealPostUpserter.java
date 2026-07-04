package dev.hogumeter.core.adapter.persistence;

import java.time.Instant;

/**
 * 멱등 수집 업서트(BM-01 AC-1/AC-2). (site, post_id) 자연키로 조회해 있으면 갱신, 없으면 삽입.
 * 같은 내용 재수집은 행 수 불변(멱등), 상태 변화는 기존 행에 반영. UNIQUE 제약이 최종 방어선.
 */
public class RawDealPostUpserter {

	private final RawDealPostRepository repository;

	public RawDealPostUpserter(RawDealPostRepository repository) {
		this.repository = repository;
	}

	public RawDealPost upsert(String site, String postId, String url, String title, Instant capturedAt, String status) {
		return repository.findBySiteAndPostId(site, postId)
				.map(existing -> {
					existing.refreshFrom(url, title, capturedAt, status);
					return repository.save(existing);
				})
				.orElseGet(() -> repository.save(new RawDealPost(site, postId, url, title, capturedAt, status)));
	}
}
