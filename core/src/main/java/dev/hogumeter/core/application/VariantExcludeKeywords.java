package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.domain.deal.ExcludeKeywordPolicy;
import dev.hogumeter.core.domain.deal.ExcludeKeywordPolicy.Mode;
import dev.hogumeter.core.domain.deal.ExcludeKeywordPolicy.Verdict;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 제외 키워드로 variant의 표본을 거른다(Q-28·C-5). 제목이 걸리는 딜(리퍼·벌크 등)은 <b>전 통계에서 제외</b>한다 —
 * 신품 기준가에 중고·묶음이 섞이면 기준가가 실제보다 아래로 끌린다.
 *
 * <p><b>왜 조회 시점인가</b>: 제외 키워드는 사용자가 언제든 고치는 손잡이다. 저장 시점(ingest)에 판정해
 * 태그로 굳히면, 나중에 "리퍼"를 목록에 넣어도 이미 들어온 리퍼 딜이 소급 제외되지 않는다. 그래서 매 조회마다
 * 지금 목록에 대고 판정한다. 판정은 순수 도메인 {@link ExcludeKeywordPolicy}(지금까지 소비처 0이었다).
 *
 * <p><b>왜 엔티티 단계인가</b>: 제목은 {@code raw_deal_post}에 있고 {@code DealEvent}(도메인)엔 없다.
 * 도메인에 제목을 끌어들이지 않고, 매핑 전 엔티티에서 걸러 살아남은 것만 도메인으로 넘긴다.
 *
 * <p>모드(EXCLUDE/LABEL)는 <b>가시성만</b> 가른다 — 둘 다 통계에선 빠진다(C-5). 그래서 표본을 거를 땐
 * 모드와 무관하게 "걸리면 빼고", 여기선 EXCLUDE로 판정한다. LABEL 딜을 ⚠️로 노출하는 표시는 후속 UI다.
 */
@Service
public class VariantExcludeKeywords {

	private final AlertPolicyRepository policies;
	private final DealEventSourceRepository sources;
	private final RawDealPostRepository rawPosts;
	private final ExcludeKeywordPolicy policy = new ExcludeKeywordPolicy();

	public VariantExcludeKeywords(AlertPolicyRepository policies, DealEventSourceRepository sources,
			RawDealPostRepository rawPosts) {
		this.policies = policies;
		this.sources = sources;
		this.rawPosts = rawPosts;
	}

	/**
	 * 제외 키워드에 걸리는 딜을 뺀 목록을 돌려준다. 키워드가 없으면(대부분) 원본을 그대로 — 제목 조회조차 하지 않는다.
	 */
	public List<DealEventEntity> filter(long variantId, List<DealEventEntity> deals) {
		Set<String> keywords = keywordsFor(variantId);
		if (keywords.isEmpty() || deals.isEmpty()) {
			return deals;
		}
		return deals.stream().filter(deal -> !hitsAnyKeyword(deal, keywords)).toList();
	}

	private Set<String> keywordsFor(long variantId) {
		return policies.findByVariantId(variantId)
				.map(p -> Set.copyOf(p.getExcludeKeywords()))
				.orElseGet(Set::of);
	}

	/** 이 딜의 <b>어느 원문 제목</b>이라도 제외 키워드에 걸리면 제외한다 — 신품 기준가를 지키는 쪽으로 보수적으로. */
	private boolean hitsAnyKeyword(DealEventEntity deal, Set<String> keywords) {
		for (DealEventSourceEntity source : sources.findByDealEventId(deal.getId())) {
			String title = rawPosts.findById(source.getRawDealPostId()).map(RawDealPost::getTitle).orElse(null);
			if (title != null && policy.evaluate(title, keywords, Mode.EXCLUDE) == Verdict.EXCLUDED) {
				return true;
			}
		}
		return false;
	}
}
