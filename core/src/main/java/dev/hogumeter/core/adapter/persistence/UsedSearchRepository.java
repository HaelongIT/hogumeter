package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedSearchRepository extends JpaRepository<UsedSearchEntity, Long> {

	List<UsedSearchEntity> findByProductId(Long productId);
}
