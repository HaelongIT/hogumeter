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
 * 승격·기각 UI가 없어 아직 전부 기본값이다(Q-15). 읽기 전용 SQL은 엔티티를 건드리지 않고 진실을 본다.
 *
 * <p>1인용 규모라 페이지네이션 없음(과최적화 금지, PERF-04).
 */
@Service
public class GetReviewQueueUseCase {

	/**
	 * <b>같은 근거는 한 행이고, 재적재 횟수는 {@code occurrences}로 드러난다(Q-27 ④ 해소).</b>
	 *
	 * <p>{@code findUnprocessed()}는 {@code deal_event_source} 링크가 없는 원문을 미처리로 본다.
	 * 매칭에 실패한 원문은 딜을 만들지 않아 링크도 안 생기므로 <b>매 틱 다시 스캔된다</b>(60초 주기면 원문
	 * 하나당 하루 1,440번). 예전엔 그때마다 새 행이 쌓여, 이 조회가 {@code (type,payload)}로 접어 {@code
	 * count(*)}로 세야 했다. 이제 {@link IngestDealsUseCase}가 {@code dedup_key}로 <b>한 행에 접고</b>
	 * 재적재를 {@code occurrences} 컬럼으로 센다 — 이 조회는 그 컬럼을 그대로 읽는다(그룹핑 불필요).
	 *
	 * <p>접어서 세는 이유는 그대로다: 조용히 지우면 결함이 사라진 것처럼 보인다. {@code occurrences}가
	 * 크다는 것은 그 자체로 "재처리 멱등이 없다"는 증거다 — 그 원문이 계속 미상으로 다시 온다는 뜻이다.
	 *
	 * <p>원문 링크는 유형마다 다른 길로 닿는다 — UNCLASSIFIED는 {@code raw_deal_post}를 직접 가리키고,
	 * OUTLIER_LOWER는 {@code deal_event}를 가리키는데 그 테이블엔 url 컬럼이 없어
	 * {@code deal_event_source}를 거쳐야 한다(병합된 딜은 원문이 여럿이라 가장 이른 것 하나).
	 * 잇지 못하면 {@code null}이다 — 죽은 링크를 그리느니 링크가 없는 편이 낫다.
	 */
	private static final String PENDING = """
			select q.id,
			       q.type,
			       q.occurrences,
			       q.created_at  as first_seen_at,
			       q.last_seen_at,
			       q.payload::text as payload,
			       coalesce(post.url, outlier.url) as source_url,
			       subject.name as subject,
			       coalesce(conditions.tags, '') as conditions
			  from review_queue_item q
			  left join raw_deal_post post
			         on q.type = 'UNCLASSIFIED'
			        and post.id = nullif(q.payload ->> 'rawDealPostId', '')::bigint
			  left join lateral (
			       select rp.url
			         from deal_event_source des
			         join raw_deal_post rp on rp.id = des.raw_deal_post_id
			        where q.type = 'OUTLIER_LOWER'
			          and des.deal_event_id = nullif(q.payload ->> 'dealEventId', '')::bigint
			        order by des.id
			        limit 1
			  ) outlier on true
			  left join lateral (
			       select p.name || ' — ' || v.label as name
			         from deal_event de
			         join variant v on v.id = de.variant_id
			         join product p on p.id = v.product_id
			        where q.type = 'OUTLIER_LOWER'
			          and de.id = nullif(q.payload ->> 'dealEventId', '')::bigint
			  ) subject on true
			  left join lateral (
			       -- **왜 싸 보이는가.** `카할`이면 특정 카드 보유자만, `배송비미상`이면 하한이다.
			       -- 정렬은 로케일이 아니라 바이트 순서로 고정한다(같은 태그 집합이 같은 문자열이 되게).
			       select string_agg(tag, ',' order by tag collate "C") as tags
			         from deal_event de
			         cross join lateral unnest(de.applied_conditions) as tag
			        where q.type = 'OUTLIER_LOWER'
			          and de.id = nullif(q.payload ->> 'dealEventId', '')::bigint
			  ) conditions on true
			 where q.status = 'PENDING'
			 order by q.last_seen_at desc, q.id desc
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
				row.getString("subject"),
				splitTags(row.getString("conditions")),
				payloadOf(row.getString("payload")));
	}

	/** 빈 문자열을 {@code split}하면 {@code [""]}가 된다 — 화면이 이름 없는 태그를 그린다. */
	private static List<String> splitTags(String aggregated) {
		if (aggregated == null || aggregated.isBlank()) {
			return List.of();
		}
		return List.of(aggregated.split(","));
	}

	/** 이름을 붙이기 전의 한 행. `payload`에서 후보 id를 꺼내는 책임도 여기 있다. */
	private record Row(long id, ReviewQueueType type, int occurrences, Instant firstSeenAt, Instant lastSeenAt,
			String sourceUrl, String subject, List<String> conditions, Map<String, Object> payload) {

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
			return new PendingItem(id, type, occurrences, firstSeenAt, lastSeenAt, sourceUrl, subject, candidates,
					conditions, payload);
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
	 * @param subject 이 항목이 **무엇에 대한 것인가**("제품 — variant"). 미상 항목은 정의상 대상이 없어
	 *     {@code null}이다. 이상치인데 딜이 미상이면 역시 {@code null} — 화면이 그 사실을 말한다.
	 * @param candidateProducts 후보 제품 이름. 사라진 제품은 {@code #id}로 남긴다 — 조용히 빼면
	 *     "후보 2개"가 "후보 1개"가 되고 근거가 줄어든 것을 아무도 모른다.
	 * @param conditions 이 딜의 조건 태그(BM-02 AC-2). <b>이상치가 왜 싸 보이는지</b>를 말한다 —
	 *     `카할`이면 특정 카드 보유자만 그 가격이고, `배송비미상`이면 저장된 값이 하한이다.
	 *     즉 이상치가 아니라 <b>정상</b>일 수 있다. 미상 항목은 딜이 없으므로 항상 빈 목록이다.
	 */
	public record PendingItem(long id, ReviewQueueType type, int occurrences, Instant firstSeenAt,
			Instant lastSeenAt, String sourceUrl, String subject, List<String> candidateProducts,
			List<String> conditions, Map<String, Object> payload) {
	}
}
