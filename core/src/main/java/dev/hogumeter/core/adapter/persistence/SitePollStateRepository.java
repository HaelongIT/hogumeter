package dev.hogumeter.core.adapter.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SitePollStateRepository extends JpaRepository<SitePollStateEntity, String> {

	/**
	 * 모든 사이트 중 <b>가장 이른</b> 성공 폴링 시각. 관측시계는 "우리가 가장 늦게까지 못 본 곳"을
	 * 따라야 딜을 놓치지 않는다 — max를 쓰면 한 사이트만 살아 있어도 다른 사이트의 공백이 딜 노화로
	 * 오독된다(docs/03 3-2 "무지를 부재로 오독 방지"). 행이 없으면 empty.
	 */
	@Query("select min(s.lastSuccessfulPollAt) from SitePollStateEntity s")
	Optional<Instant> earliestSuccessfulPoll();
}
