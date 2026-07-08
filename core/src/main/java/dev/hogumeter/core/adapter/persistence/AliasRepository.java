package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AliasRepository extends JpaRepository<AliasEntity, Long> {

	List<AliasEntity> findByProductId(Long productId);
}
