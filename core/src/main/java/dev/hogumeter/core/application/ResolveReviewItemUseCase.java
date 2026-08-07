package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.matching.AliasDictionary;
import dev.hogumeter.core.domain.matching.Matcher;
import dev.hogumeter.core.domain.matching.TitleNormalizer;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미상 큐 승격·기각(Q-15 쓰기). 읽기(GetReviewQueueUseCase)까지만 있고 쓰기가 없어, 순수 도메인 {@code
 * DealEvent.promoteFromOutlier()}·{@code reject()}는 프로덕션 호출자가 0이었고 {@code
 * review_queue_item.status}·{@code resolved_at}·{@code channel}은 항상 기본값인 죽은 컬럼이었다.
 *
 * <p><b>승격(promote)</b>: 이상치 오탐을 사람이 정상으로 판정 → {@code promoteFromOutlier}(플래그 해제,
 * 표본 복귀). <b>기각(reject)</b>: 사기·낚시로 판정 → {@code reject}(영구 제외, 재수집돼도 복귀 없음,
 * BM-05 AC-3). 판단은 순수 도메인이 하고 여기선 그 결과를 엔티티에 반영하는 IO만 한다.
 *
 * <p><b>미상(UNCLASSIFIED) 승격(Q-15 ①, 2026-07-31)</b>: 매칭이 실패한 원문엔 딜이 없어, 승격하려면
 * 사람이 <b>어느 variant인지</b> 골라야 한다. {@code variantId}가 주어지면 {@link IngestDealsUseCase#confirmDeal}
 * 을 그대로 재사용해 딜을 만든다(병합/신규 저장 규칙은 그 한 곳이 정본 — 자동 매칭과 사람 승격이 서로 다른
 * 규칙을 쓰면 둘이 조용히 갈린다). 수요축 값은 <b>지어내지 않는다</b>(null) — 매칭이 실패해 애초에 제목에서
 * 판별하지 못했던 값이다. SPLIT 제품이면 {@code confirmDeal}이 이미 하던 대로 DEMAND_UNKNOWN 큐로 다시
 * 올려 사람이 마저 분류한다(Q-66 ① E). {@code variantId}가 없으면 여전히 막는다
 * ({@link UnclassifiedPromoteNotSupportedException}) — DEMAND_UNKNOWN·KEYWORD_SUGGEST 유형도 마찬가지로
 * 승격 개념이 없다(딜은 이미 있거나 애초에 딜 대상이 아니다).
 *
 * <p><b>별칭 학습(BM-03 AC-4, 2026-08-05)</b>: 이 승격이 바로 {@link Matcher#confirm}이 뜻하는 "사람 확정"
 * 순간이다 — 승격하면 그 원문 제목을 별칭 사전({@code alias_dictionary})에 저장해, 다음엔 같은 표현이 자동
 * CONFIRMED되게 한다. {@code alias_dictionary}의 {@code unique(product_id, alias)}를 뚫지 않도록, 이미
 * 아는(정규화 동일) 표현이면 다시 넣지 않는다.
 *
 * <p>Q-27 ④로 같은 근거는 이제 <b>한 행</b>이라, 그 한 행을 처리하면 끝난다(예전엔 매 틱 쌓인 N행이 남았다).
 * 이미 처리된(또는 없는) 항목은 {@link ReviewItemNotFoundException} — 처리는 PENDING 행에만
 * 원자적으로 건다({@code where status='PENDING'}). {@code status}·{@code resolved_at}·{@code channel}은
 * 엔티티가 매핑하지 않으므로 네이티브 SQL로 다룬다(GetReviewQueueUseCase와 같은 수법).
 *
 * <p><b>BE-07 검토(코드리뷰 20260806)</b>: {@code readPending}(SELECT) → 부수효과(딜 생성·별칭 학습)
 * → {@code resolve}(원자적 UPDATE) 순서만 보면 "먼저 통과한 두 요청이 둘 다 부수효과를 실행할 수 있다"는
 * 우려가 그럴듯해 보이지만, {@code promote}·{@code reject} 전체가 <b>하나의 {@code @Transactional}</b>
 * 이라 실제로는 안전하다 — 두 번째 요청의 {@code resolve} UPDATE는 같은 행에 대한 PostgreSQL 행 잠금 때문에
 * 첫 번째 트랜잭션이 커밋할 때까지 블록되고, 커밋 후 재평가한 {@code WHERE status='PENDING'}이 거짓이 돼
 * 0행이 갱신된다 → {@link ReviewItemNotFoundException}이 던져지고 → Spring이 그 트랜잭션 전체(부수효과
 * 포함)를 롤백한다. 즉 최종적으로 커밋되는 부수효과는 항상 하나뿐이다. 별도 스레드로 재현을 시도했으나
 * (신뢰할 수 있는 동시성 테스트를 만들 계측 지점이 없어 결정적 재현은 어려웠다) 이 트랜잭션 경계 분석이
 * 근거다 — 다음에 이 코드를 만지는 사람을 위해 결론만 남긴다: 실제 결함 아님.
 */
@Service
public class ResolveReviewItemUseCase {

	private static final String OUTLIER_LOWER = "OUTLIER_LOWER";
	private static final String UNCLASSIFIED = "UNCLASSIFIED";

	private final JdbcTemplate jdbc;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final RawDealPostRepository rawPosts;
	private final VariantRepository variants;
	private final AliasRepository aliases;
	private final IngestDealsUseCase ingestDeals;
	private final Matcher matcher = new Matcher();

	public ResolveReviewItemUseCase(JdbcTemplate jdbc, DealEventRepository dealEvents, DealEventMapper mapper,
			RawDealPostRepository rawPosts, VariantRepository variants, AliasRepository aliases,
			IngestDealsUseCase ingestDeals) {
		this.jdbc = jdbc;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.rawPosts = rawPosts;
		this.variants = variants;
		this.aliases = aliases;
		this.ingestDeals = ingestDeals;
	}

	/** 승격 — 이상치 오탐을 정상으로. 미상 항목은 {@code variantId} 없이는 지원하지 않는다. REST(웹) 경로. */
	@Transactional
	public void promote(long reviewItemId) {
		promote(reviewItemId, (Long) null);
	}

	/** {@code variantId}는 미상(UNCLASSIFIED) 승격에만 쓰인다 — 이상치는 이미 딜이 있어 무시한다. */
	@Transactional
	public void promote(long reviewItemId, Long variantId) {
		promote(reviewItemId, variantId, "WEB");
	}

	/**
	 * 어느 채널로 처리됐는지만 지정(variant 선택 없음) — 텔레그램 인라인 버튼 승격({@link ReviewCallbackRouter})
	 * 이 쓴다. 아직 variant 선택 UI가 없어 미상 항목은 이 경로로는 여전히 거절된다(이상치는 그대로 동작).
	 */
	@Transactional
	public void promote(long reviewItemId, String channel) {
		promote(reviewItemId, null, channel);
	}

	/** 어느 채널(WEB·TELEGRAM)로 처리됐는지 남긴다 — 인라인 버튼 승격(Q-15)은 TELEGRAM으로 온다. */
	@Transactional
	public void promote(long reviewItemId, Long variantId, String channel) {
		Item item = readPending(reviewItemId);
		if (UNCLASSIFIED.equals(item.type())) {
			promoteUnclassified(reviewItemId, item, variantId);
			resolve(reviewItemId, "CONFIRMED", channel);
			return;
		}
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

	/**
	 * {@code variantId} 없이는 여전히 막는다(지어내지 않는다). 있으면 {@code IngestDealsUseCase.confirmDeal}
	 * 로 자동 매칭과 같은 규칙으로 딜을 만들고(병합/신규는 그 메서드가 정한다), 그 원문 제목을 별칭으로
	 * 학습한다(BM-03 AC-4) — 수요축 값은 null(미상)로 넘긴다: 매칭이 실패해 원래 판별하지 못한 값을 여기서
	 * 지어낼 근거가 없다.
	 */
	private void promoteUnclassified(long reviewItemId, Item item, Long variantId) {
		if (variantId == null) {
			throw new UnclassifiedPromoteNotSupportedException(reviewItemId);
		}
		VariantEntity variant = variants.findById(variantId).orElseThrow(() -> new VariantNotFoundException(variantId));
		RawDealPost post = rawPosts.findById(item.rawDealPostId())
				.orElseThrow(() -> new ReviewItemNotFoundException(reviewItemId));
		ingestDeals.confirmDeal(post, variantId, null);
		learnAlias(post, variant.getProductId());
	}

	/**
	 * {@link Matcher#confirm}(BM-03 AC-4, 이제 이 지점이 유일한 호출자)로 정규화한 표현을 별칭 사전에
	 * 축적한다. {@code alias_dictionary}는 {@code unique(product_id, alias)}라 이미 아는(정규화 동일)
	 * 표현이면 다시 넣지 않는다 — 안 그러면 같은 UNCLASSIFIED 원문이 또 승격될 때(재현율 우선 매칭이라
	 * 흔하다) 유니크 제약을 뚫는다.
	 */
	private void learnAlias(RawDealPost post, Long productId) {
		AliasDictionary learned = matcher.confirm(AliasDictionary.of(Map.of()), post.getTitle(), productId);
		String normalized = learned.aliases().keySet().iterator().next();
		boolean known = aliases.findByProductId(productId).stream()
				.anyMatch(existing -> normalized.equals(TitleNormalizer.joined(existing.getAlias())));
		if (!known) {
			// 저장은 원문(raw) 그대로 — CatalogProjection.aliasDictionary()가 읽을 때 다시 정규화한다
			// (RegisterProductUseCase가 등록 별칭을 저장하는 것과 같은 관례, 정규화 사본을 늘리지 않는다).
			aliases.save(new AliasEntity(productId, post.getTitle()));
		}
	}

	private Item readPending(long reviewItemId) {
		List<Item> rows = jdbc.query("""
				select type, status,
				       nullif(payload ->> 'dealEventId', '')::bigint as deal_event_id,
				       nullif(payload ->> 'rawDealPostId', '')::bigint as raw_deal_post_id
				  from review_queue_item where id = ?
				""",
				(rs, n) -> new Item(rs.getString("type"), rs.getString("status"),
						(Long) rs.getObject("deal_event_id"), (Long) rs.getObject("raw_deal_post_id")),
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

	private record Item(String type, String status, Long dealEventId, Long rawDealPostId) {
	}
}
