package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealEventSourceRepository extends JpaRepository<DealEventSourceEntity, Long> {

	/**
	 * BE-16(코드리뷰 20260806): order by 없는 파생 쿼리는 PostgreSQL이 행 순서를 보장하지 않는다 —
	 * {@link DealEventMapper#toDomain}의 "대표 원문" 선택({@code src.get(0)})이 재현 불가능해질 수
	 * 있었다. id 오름차순(=먼저 저장된 소스 우선)으로 명시 정렬한다. 메서드 이름은 그대로 두고
	 * {@code @Query}로 정렬만 추가했다 — 파생 쿼리 이름을 바꿔 별도 메서드+default 위임으로 가면
	 * 기존 호출부(8곳)는 그대로 두더라도 새 메서드 자체는 인터페이스 밖에서 아무도 안 불러
	 * {@code check-repository-readers.sh}가 "호출자 0"으로 잡는다(실제로 겪음).
	 */
	@Query("select s from DealEventSourceEntity s where s.dealEventId = :dealEventId order by s.id asc")
	List<DealEventSourceEntity> findByDealEventId(@Param("dealEventId") Long dealEventId);
}
