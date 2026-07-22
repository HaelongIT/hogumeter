package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * listing 조회(USED-02). 생애주기 접기는 <b>검색 단위</b>로 돈다 — 한 배치를 접으려면 그 검색의 매물을
 * 전부 알아야 하므로 단건 조회는 필요 없다. (단건 조회 메서드를 뒀더니 프로덕션 호출자가 0이었고
 * {@code check-repository-readers}가 잡았다 — 편의 메서드도 소비처가 없으면 죽은 코드다.)
 */
public interface ListingRepository extends JpaRepository<ListingEntity, Long> {

	List<ListingEntity> findByUsedSearchId(Long usedSearchId);
}
