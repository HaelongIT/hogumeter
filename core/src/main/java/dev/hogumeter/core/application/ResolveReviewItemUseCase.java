package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.domain.deal.DealEvent;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미상 큐 승격·기각(Q-15 쓰기). 읽기(GetReviewQueueUseCase)까지만 있고 쓰기가 없어, 순수 도메인
 * {@code DealEvent.promoteFromOutlier()}·{@code reject()}는 프로덕션 호출자가 0이었고 {@code
 * review_queue_item.status}·{@code resolved_at}·{@code channel}은 항상 기본값인 죽은 컬럼이었다.
 *
 * <p><b>승격(promote)</b>: 이상치 오탐을 사람이 정상으로 판정 → {@code promoteFromOutlier}(플래그 해제,
 * 표본 복귀). <b>기각(reject)</b>: 사기·낚시로 판정 → {@code reject}(영구 제외, 재수집돼도 복귀 없음,
 * BM-05 AC-3). 판단은 순수 도메인이 하고 여기선 그 결과를 엔티티에 반영하는 IO만 한다.
 *
 * <p>Q-27 ④로 같은 근거는 이제 <b>한 행</b>이라, 그 한 행을 처리하면 끝난다(예전엔 매 틱 쌓인 N행이 남았다).
 * 이미 처리된(또는 없는) 항목은 {@link ReviewItemNotFoundException} — 처리는 PENDING 행에만
 * 원자적으로 건다({@code where status='PENDING'}). {@code status}·{@code resolved_at}·{@code channel}은
 * 엔티티가 매핑하지 않으므로 네이티브 SQL로 다룬다(GetReviewQueueUseCase와 같은 수법).
 *
 * <p>미상(UNCLASSIFIED) 항목은 딜이 없다 — 기각은 큐에서 내리기만 하고, 승격은 variant 지정이 필요해
 * 아직 막는다({@link UnclassifiedPromoteNotSupportedException}). 지어내 딜을 만들지 않는다.
 */
@Service
public class ResolveReviewItemUseCase {

	private static final String OUTLIER_LOWER = "OUTLIER_LOWER";

	private final JdbcTemplate jdbc;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;

	public ResolveReviewItemUseCase(JdbcTemplate jdbc, DealEventRepository dealEvents, DealEventMapper mapper) {
		this.jdbc = jdbc;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
	}

	/** 승격 — 이상치 오탐을 정상으로. 미상 항목은 지원하지 않는다(variant 지정 필요). REST(웹) 경로. */
	@Transactional
	public void promote(long reviewItemId) {
		promote(reviewItemId, "WEB");
	}

	/** 어느 채널(WEB·TELEGRAM)로 처리됐는지 남긴다 — 인라인 버튼 승격(Q-15)은 TELEGRAM으로 온다. */
	@Transactional
	public void promote(long reviewItemId, String channel) {
		Item item = readPending(reviewItemId);
		if (!OUTLIER_LOWER.equals(item.type())) {
			throw new UnclassifiedPromoteNotSupportedException(reviewItemId);
		}
		applyToDeal(item, true);
		resolve(reviewItemId, "CONFIRMED", channel);
	}

	/** 기각 — 사기·낚시로 영구 제외. 미상 항목은 딜이 없어 큐에서 내리기만 한다. REST(웹) 경로. */
	@Transactional
	public void reject(long reviewItemId) {
		reject(reviewItemId, "WEB");
	}

	@Transactional
	public void reject(long reviewItemId, String channel) {
		Item item = readPending(reviewItemId);
		if (OUTLIER_LOWER.equals(item.type())) {
			applyToDeal(item, false);
		}
		resolve(reviewItemId, "REJECTED", channel);
	}

	private Item readPending(long reviewItemId) {
		List<Item> rows = jdbc.query("""
				select type, status, nullif(payload ->> 'dealEventId', '')::bigint as deal_event_id
				  from review_queue_item where id = ?
				""",
				(rs, n) -> new Item(rs.getString("type"), rs.getString("status"),
						(Long) rs.getObject("deal_event_id")),
				reviewItemId);
		if (rows.isEmpty() || !"PENDING".equals(rows.get(0).status())) {
			throw new ReviewItemNotFoundException(reviewItemId);
		}
		return rows.get(0);
	}

	/** 이상치 딜에 순수 도메인 전이를 반영한다. {@code promote=true}면 승격, 아니면 기각. */
	private void applyToDeal(Item item, boolean promote) {
		if (item.dealEventId() == null) {
			return; // 근거 딜이 없다 — 상태만 바꾼다(방어적: 정상적으론 OUTLIER_LOWER엔 딜 id가 있다)
		}
		DealEventEntity entity = dealEvents.findById(item.dealEventId())
				.orElseThrow(() -> new ReviewItemNotFoundException(item.dealEventId()));
		DealEvent domain = mapper.toDomain(entity);
		DealEvent result = promote ? domain.promoteFromOutlier() : domain.reject();
		entity.setOutlierFlag(result.outlierFlag());
		entity.setPermanentlyExcluded(result.permanentlyExcluded());
	}

	/**
	 * PENDING 행에만 원자적으로 처리 표시. {@code channel}은 어디로 처리됐나(WEB·TELEGRAM) — CHECK가 그 둘만
	 * 허용한다(V1). 0행이면 그 사이 누가 처리했다는 뜻 — 없는 것과 같이 취급한다(멱등: 두 번 눌러도 두 번째는 404).
	 */
	private void resolve(long reviewItemId, String status, String channel) {
		int updated = jdbc.update(
				"update review_queue_item set status = ?, channel = ?, resolved_at = now() "
						+ "where id = ? and status = 'PENDING'",
				status, channel, reviewItemId);
		if (updated == 0) {
			throw new ReviewItemNotFoundException(reviewItemId);
		}
	}

	private record Item(String type, String status, Long dealEventId) {
	}
}
