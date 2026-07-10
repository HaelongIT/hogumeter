package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// Boot 4는 Jackson 3다 — databind·core는 `tools.jackson`, 애노테이션만 `com.fasterxml.jackson.annotation`.
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 미상 큐 조회(읽기 전용). BM-03이 매칭에 실패한 딜(UNCLASSIFIED)과 BM-05가 잡은 이상치(OUTLIER_LOWER)가
 * 여기 쌓인다.
 *
 * <p><b>이 조회가 생기기 전까지 {@code review_queue_item}은 쓰이기만 하고 아무도 읽지 않았다.</b>
 * {@link IngestDealsUseCase}가 넣고 {@code PipelineScheduler}가 세기만 했다 — 매칭이 무엇을 놓치는지
 * 사람이 볼 방법이 없었다. 놓침을 허용하는 시스템(절대 원칙 3)에서 놓친 것을 볼 수 없다면 그건 유실이다.
 *
 * <p><b>왜 JPA가 아니라 SQL인가</b>: {@code ReviewQueueItemEntity}는 {@code status}·{@code created_at}·
 * {@code channel}·{@code resolved_at}을 매핑하지 않는다(생성 시점엔 전부 기본값이라 필요가 없었다).
 * 그 파일은 다른 개발자 소유라 고치지 않는다. 읽기 전용 SQL은 엔티티를 건드리지 않고 진실을 본다.
 *
 * <p>1인용 규모라 페이지네이션 없음(과최적화 금지, PERF-04).
 */
@Service
public class GetReviewQueueUseCase {

	/**
	 * <b>같은 근거는 접어서 세고, 접었다는 사실을 {@code occurrences}로 드러낸다.</b>
	 *
	 * <p>{@code findUnprocessed()}는 {@code deal_event_source} 링크가 없는 원문을 미처리로 본다.
	 * 그런데 매칭에 실패한 원문은 딜을 만들지 않으므로 링크도 생기지 않는다 — 즉 <b>매 틱마다 같은 항목이
	 * 다시 큐에 쌓인다</b>(운영 60초 주기면 원문 하나당 하루 1,440행). docs/91 Q-27 ④에 "중복 여지"로
	 * 적혀 있으나 실측된 적이 없었다. 고치려면 {@code IngestDealsUseCase}·{@code RawDealPostRepository}를
	 * 손봐야 하는데 둘 다 다른 개발자 소유다.
	 *
	 * <p>중복을 조용히 지우면 결함이 사라진 것처럼 보인다. 그래서 <b>세어서 보여준다</b> —
	 * {@code occurrences}가 크다는 것은 그 자체로 "재처리 멱등이 없다"는 증거다.
	 *
	 * <p>원문 링크는 유형마다 다른 길로 닿는다 — UNCLASSIFIED는 {@code raw_deal_post}를 직접 가리키고,
	 * OUTLIER_LOWER는 {@code deal_event}를 가리키는데 그 테이블엔 url 컬럼이 없어
	 * {@code deal_event_source}를 거쳐야 한다(병합된 딜은 원문이 여럿이라 가장 이른 것 하나).
	 * 잇지 못하면 {@code null}이다 — 죽은 링크를 그리느니 링크가 없는 편이 낫다.
	 */
	private static final String PENDING = """
			with grouped as (
			     select min(q.id)         as id,
			            q.type            as type,
			            q.payload         as payload,
			            count(*)          as occurrences,
			            min(q.created_at) as first_seen_at,
			            max(q.created_at) as last_seen_at
			       from review_queue_item q
			      where q.status = 'PENDING'
			      group by q.type, q.payload
			)
			select g.id,
			       g.type,
			       g.occurrences,
			       g.first_seen_at,
			       g.last_seen_at,
			       g.payload::text as payload,
			       coalesce(post.url, outlier.url) as source_url
			  from grouped g
			  left join raw_deal_post post
			         on g.type = 'UNCLASSIFIED'
			        and post.id = nullif(g.payload ->> 'rawDealPostId', '')::bigint
			  left join lateral (
			       select rp.url
			         from deal_event_source des
			         join raw_deal_post rp on rp.id = des.raw_deal_post_id
			        where g.type = 'OUTLIER_LOWER'
			          and des.deal_event_id = nullif(g.payload ->> 'dealEventId', '')::bigint
			        order by des.id
			        limit 1
			  ) outlier on true
			 order by g.last_seen_at desc, g.id desc
			""";

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final ProductRepository products;

	public GetReviewQueueUseCase(JdbcTemplate jdbc, ObjectMapper json, ProductRepository products) {
		this.jdbc = jdbc;
		this.json = json;
		this.products = products;
	}

	/** 처리 대기(PENDING) 항목만. 처리된 것은 큐가 아니다. */
	@Transactional(readOnly = true)
	public List<PendingItem> pending() {
		List<Row> rows = jdbc.query(PENDING, this::toRow);
		Map<Long, String> names = productNames(rows);
		return rows.stream().map(row -> row.withCandidateNames(names)).toList();
	}

	/**
	 * 후보 id를 이름으로 한 번에 푼다(N+1 금지). <b>화면이 "후보 2개"라고만 말하면 사람은 아무 판단도
	 * 못 한다</b> — id는 사람이 읽는 값이 아니다.
	 */
	private Map<Long, String> productNames(List<Row> rows) {
		Set<Long> ids = rows.stream().flatMap(row -> row.candidateIds().stream()).collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> names = new LinkedHashMap<>();
		products.findAllById(ids).forEach(product -> names.put(product.getId(), product.getName()));
		return names;
	}

	private Row toRow(ResultSet row, int rowNum) throws SQLException {
		return new Row(
				row.getLong("id"),
				ReviewQueueType.valueOf(row.getString("type")),
				row.getInt("occurrences"),
				row.getTimestamp("first_seen_at").toInstant(),
				row.getTimestamp("last_seen_at").toInstant(),
				row.getString("source_url"),
				payloadOf(row.getString("payload")));
	}

	/** 이름을 붙이기 전의 한 행. `payload`에서 후보 id를 꺼내는 책임도 여기 있다. */
	private record Row(long id, ReviewQueueType type, int occurrences, Instant firstSeenAt, Instant lastSeenAt,
			String sourceUrl, Map<String, Object> payload) {

		/** payload는 jsonb다 — 기대한 타입이 온다는 보장이 없다. 숫자가 아닌 값은 무시한다. */
		List<Long> candidateIds() {
			if (!(payload.get("productCandidates") instanceof List<?> raw)) {
				return List.of();
			}
			return raw.stream()
				.filter(Number.class::isInstance)
				.map(value -> ((Number) value).longValue())
				.toList();
		}

		/** 사라진 제품을 조용히 빼면 "후보 2개"가 "후보 1개"가 된다 — 근거가 줄어든 걸 아무도 모른다. */
		PendingItem withCandidateNames(Map<Long, String> names) {
			List<String> candidates = candidateIds().stream()
				.map(candidateId -> names.getOrDefault(candidateId, "#" + candidateId))
				.toList();
			return new PendingItem(id, type, occurrences, firstSeenAt, lastSeenAt, sourceUrl, candidates, payload);
		}
	}

	private Map<String, Object> payloadOf(String raw) {
		try {
			return json.readValue(raw, new TypeReference<Map<String, Object>>() { });
		}
		catch (JacksonException malformed) {
			// payload는 not null jsonb다. 여기 오면 스키마가 아니라 우리가 틀린 것이다.
			throw new IllegalStateException("review_queue_item.payload를 읽지 못했습니다: " + raw, malformed);
		}
	}

	/**
	 * 큐 한 항목(같은 근거는 하나로 접힌 것). {@code payload}는 유형별 근거를 그대로 담는다 —
	 * 화면이 유형을 모르더라도 사람은 근거를 볼 수 있어야 한다(과대약속 금지).
	 *
	 * @param id 접힌 행들 중 가장 이른 것. 승격·기각(쓰기)이 생기면 <b>이 id로는 부족하다</b> —
	 *     그때는 접힌 행 전부를 처리해야 한다(docs/91 Q-15·Q-27 ④).
	 * @param occurrences 같은 근거가 큐에 들어간 횟수. <b>1보다 크면 재처리 멱등이 없다는 뜻</b>이다.
	 * @param sourceUrl 원문 링크. 잇지 못하면 {@code null}.
	 * @param candidateProducts 후보 제품 이름. 사라진 제품은 {@code #id}로 남긴다 — 조용히 빼면
	 *     "후보 2개"가 "후보 1개"가 되고 근거가 줄어든 것을 아무도 모른다.
	 */
	public record PendingItem(long id, ReviewQueueType type, int occurrences, Instant firstSeenAt,
			Instant lastSeenAt, String sourceUrl, List<String> candidateProducts, Map<String, Object> payload) {
	}
}
