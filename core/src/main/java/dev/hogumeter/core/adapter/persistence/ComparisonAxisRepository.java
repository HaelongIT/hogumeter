package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonAxisRepository extends JpaRepository<ComparisonAxisEntity, Long> {

	List<ComparisonAxisEntity> findByProductId(Long productId);

	/** 같은 이름의 축이 이미 있는가(unique(product_id, name)) — 정의는 추가 전용이라 중복을 걸러야 한다. */
	Optional<ComparisonAxisEntity> findByProductIdAndName(Long productId, String name);
}
