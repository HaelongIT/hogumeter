package dev.hogumeter.core.application;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 ⑤ "N회째 미확인"의 쓰는 쪽(V17) — 다이제스트에 실렸던 큐 항목의 {@code digest_appearances}를
 * +1 한다. {@code SendDigestUseCase}가 <b>전 분할 성공 시에만</b> 호출한다(DIG-02 원자성과 같은 규칙 —
 * 안 나간 발송을 "실렸다"고 세지 않는다).
 *
 * <p><b>왜 JPA가 아니라 SQL인가</b>: {@code ReviewQueueItemEntity}에 이 컬럼을 매핑하지 않는다 —
 * {@code IngestDealsUseCase}·{@code ResolveReviewItemUseCase} 등 다른 writer가 엔티티를 저장할 때
 * 이 값을 건드릴 표면을 늘리지 않기 위해서다("이 컬럼은 발송 배선만 쓴다"는 계약을 코드로 드러낸다,
 * {@code core-java} 규칙).
 */
@Service
public class IncrementDigestQueueAppearancesUseCase {

	private static final String INCREMENT = "update review_queue_item set digest_appearances = digest_appearances + 1 where id = ?";

	private final JdbcTemplate jdbc;

	public IncrementDigestQueueAppearancesUseCase(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return 실제로 갱신된 행 수(이미 지워졌거나 처리된 id는 0). */
	@Transactional
	public int increment(List<Long> ids) {
		int touched = 0;
		for (Long id : ids) {
			touched += jdbc.update(INCREMENT, id);
		}
		return touched;
	}
}
