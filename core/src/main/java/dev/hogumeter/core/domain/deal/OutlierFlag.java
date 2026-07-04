package dev.hogumeter.core.domain.deal;

/**
 * 양방향 이상치 플래그(BM-05). NONE(정상) / UPPER(조용히 제외·무알림) / LOWER(제외하되 🔥 최우선 알림).
 * V1__init.sql deal_event.outlier_flag. BM-06은 NONE 외 전부 기준가 표본에서 제외한다.
 */
public enum OutlierFlag {
	NONE,
	UPPER,
	LOWER
}
