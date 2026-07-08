package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAxisRepository extends JpaRepository<ProductAxisEntity, Long> {

	List<ProductAxisEntity> findByProductId(Long productId);
}
