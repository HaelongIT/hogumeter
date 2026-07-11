package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.used.BonusMode;
import java.util.List;

/**
 * 중고 검색 등록 명령(USED-01). 제품 아래 UsedSearch 추가 — required(AND)/bonusGroups(OR, 모드별)/
 * exclude(NOT)/목표가/폴링주기. platform은 v1 BUNJANG 고정(유스케이스가 세팅).
 *
 * @param pollIntervalMin 폴링 주기(분). null·10 미만이면 하한 10으로 강제(MARKETPLACE, SEC-08).
 */
public record RegisterUsedSearchCommand(
		long productId,
		List<String> required,
		List<BonusGroupCommand> bonusGroups,
		List<String> exclude,
		Long targetPrice,
		Integer pollIntervalMin) {

	public record BonusGroupCommand(List<String> keywords, BonusMode mode) {
	}
}
