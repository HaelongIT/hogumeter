package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RawDealPostRepository extends JpaRepository<RawDealPost, Long> {

	Optional<RawDealPost> findBySiteAndPostId(String site, String postId);

	/** deal_event로 아직 처리되지 않은(소스 링크가 없는) 원문 — 수집 파이프라인 입력. */
	@Query("select r from RawDealPost r "
			+ "where r.id not in (select s.rawDealPostId from DealEventSourceEntity s)")
	List<RawDealPost> findUnprocessed();
}
