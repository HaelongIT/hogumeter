package dev.hogumeter.core.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawDealPostRepository extends JpaRepository<RawDealPost, Long> {

	Optional<RawDealPost> findBySiteAndPostId(String site, String postId);
}
