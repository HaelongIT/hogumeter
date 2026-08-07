package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

	/** Q-91 — 기본 목록(등록·우선순위)은 보관된 제품을 숨긴다. */
	List<ProductEntity> findByArchivedFalse();
}
