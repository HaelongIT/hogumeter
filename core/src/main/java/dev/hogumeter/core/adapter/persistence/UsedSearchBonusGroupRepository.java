package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsedSearchBonusGroupRepository extends JpaRepository<UsedSearchBonusGroupEntity, Long> {

	List<UsedSearchBonusGroupEntity> findByUsedSearchId(Long usedSearchId);
}
