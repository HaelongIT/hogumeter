package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.used.BonusMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 중고 검색 조회(읽기 전용, {@link GetProductsUseCase}의 거울상). 등록 REST는 {@code
 * usedSearchId}만 돌려주는데, 평가기(USED-04)·비교표(USED-05) 화면은 "이 제품에 어떤 검색이
 * 등록돼 있나"를 다시 볼 방법이 필요하다.
 */
@Service
public class GetUsedSearchesUseCase {

	private final UsedSearchRepository searches;
	private final UsedSearchBonusGroupRepository bonusGroups;

	public GetUsedSearchesUseCase(UsedSearchRepository searches, UsedSearchBonusGroupRepository bonusGroups) {
		this.searches = searches;
		this.bonusGroups = bonusGroups;
	}

	@Transactional(readOnly = true)
	public List<UsedSearchView> listForProduct(long productId) {
		return searches.findByProductId(productId).stream().map(this::toView).toList();
	}

	private UsedSearchView toView(UsedSearchEntity search) {
		List<BonusGroupView> groups = bonusGroups.findByUsedSearchId(search.getId()).stream()
				.map(GetUsedSearchesUseCase::toBonusGroupView)
				.toList();
		return new UsedSearchView(search.getId(), search.getPlatform(), search.getRequiredKeywords(),
				search.getExcludeKeywords(), search.getTargetPrice(), search.getPollIntervalMin(), groups);
	}

	private static BonusGroupView toBonusGroupView(UsedSearchBonusGroupEntity e) {
		return new BonusGroupView(e.getKeywords(), e.getMode());
	}

	public record UsedSearchView(long usedSearchId, String platform, List<String> required, List<String> exclude,
			Long targetPrice, int pollIntervalMin, List<BonusGroupView> bonusGroups) {
	}

	public record BonusGroupView(List<String> keywords, BonusMode mode) {
	}
}
