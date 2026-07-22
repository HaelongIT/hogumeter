package dev.hogumeter.core.adapter.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * used_listing_observation 조회(USED-02). core는 이 계약 테이블을 <b>읽기만</b> 한다 — 쓰는 쪽은 collector다.
 *
 * <p>배치 = 같은 {@code observed_at}의 행 묶음. 워터마크 이후의 배치를 <b>시각 오름차순</b>으로 하나씩
 * 접는다 — 최신 배치만 보면 그 사이의 가격 변동·소실이 조용히 사라진다.
 */
public interface UsedListingObservationRepository extends JpaRepository<UsedListingObservationEntity, Long> {

	/**
	 * 아직 한 배치도 접지 않은 검색용(워터마크 {@code null}). "미처리"를 SQL의 {@code :after is null}로
	 * 표현하면 Postgres가 파라미터 타입을 추론하지 못해 터진다 — 갈래를 코드에서 가른다.
	 */
	@Query("select distinct o.observedAt from UsedListingObservationEntity o "
			+ "where o.usedSearchId = :searchId order by o.observedAt asc")
	List<Instant> findAllBatchTimes(@Param("searchId") Long searchId);

	@Query("select distinct o.observedAt from UsedListingObservationEntity o "
			+ "where o.usedSearchId = :searchId and o.observedAt > :after order by o.observedAt asc")
	List<Instant> findBatchTimesAfter(@Param("searchId") Long searchId, @Param("after") Instant after);

	List<UsedListingObservationEntity> findByUsedSearchIdAndObservedAt(Long usedSearchId, Instant observedAt);
}
