package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealEventSourceRepository extends JpaRepository<DealEventSourceEntity, Long> {

	/**
	 * BE-16(코드리뷰 20260806): order by 없는 파생 쿼리는 PostgreSQL이 행 순서를 보장하지 않는다 —
	 * {@link DealEventMapper#toDomain}의 "대표 원문" 선택({@code src.get(0)})이 재현 불가능해질 수
	 * 있었다. id 오름차순(=먼저 저장된 소스 우선)으로 명시 정렬한다.
	 */
	List<DealEventSourceEntity> findByDealEventIdOrderByIdAsc(Long dealEventId);

	default List<DealEventSourceEntity> findByDealEventId(Long dealEventId) {
		return findByDealEventIdOrderByIdAsc(dealEventId);
	}
}
