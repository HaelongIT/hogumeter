package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중고 검색 등록 유스케이스(USED-01) — used_search + bonus_group을 한 트랜잭션으로 생성하고 id를 반환한다.
 * platform은 v1 BUNJANG 고정. 폴링 주기는 하한 10분(MARKETPLACE)으로 강제한다 — 설정으로 완화 불가(SEC-08).
 */
@Service
public class RegisterUsedSearchUseCase {

	/** SiteKind.MARKETPLACE 하한과 정합 — 사용자 입력이 이보다 짧아도 여기서 못 박는다. */
	static final int POLL_INTERVAL_FLOOR_MIN = 10;

	private final UsedSearchRepository searches;
	private final UsedSearchBonusGroupRepository bonusGroups;

	public RegisterUsedSearchUseCase(UsedSearchRepository searches, UsedSearchBonusGroupRepository bonusGroups) {
		this.searches = searches;
		this.bonusGroups = bonusGroups;
	}

	@Transactional
	public long register(RegisterUsedSearchCommand cmd) {
		int poll = cmd.pollIntervalMin() == null
				? POLL_INTERVAL_FLOOR_MIN
				: Math.max(cmd.pollIntervalMin(), POLL_INTERVAL_FLOOR_MIN);
		UsedSearchEntity search = searches.save(new UsedSearchEntity(cmd.productId(), "BUNJANG",
				cmd.required(), cmd.exclude(), cmd.targetPrice(), poll));
		long searchId = search.getId();
		for (RegisterUsedSearchCommand.BonusGroupCommand group : cmd.bonusGroups()) {
			bonusGroups.save(new UsedSearchBonusGroupEntity(searchId, group.mode(), group.keywords()));
		}
		return searchId;
	}
}
