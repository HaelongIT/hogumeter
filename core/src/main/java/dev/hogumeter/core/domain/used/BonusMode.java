package dev.hogumeter.core.domain.used;

/**
 * 보너스 키워드 그룹의 모드(USED-01, docs/used/04 AC-2·3). SORT=있으면 배지·정렬 상단이나 알림 조건엔
 * 기여하지 않음, TRIGGER=그룹 중 1개 이상 있어야 알림 발화 조건 충족.
 */
public enum BonusMode {
	SORT,
	TRIGGER
}
