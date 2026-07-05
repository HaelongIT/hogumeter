package dev.hogumeter.core.domain.alert;

/**
 * 알림 강도(AL-02 최고 강도 1발 원칙). 선언 순서 = 우선순위(앞이 강함):
 * JACKPOT(🔥 하향 이상치) > SPECIAL(특가, P25 이하) > TARGET(목표가 충족) > GOOD(괜찮은 딜, 기준가 이하).
 * NONE = 알림 없음. 여러 조건 충족 시 최고 1발만 발송하고 나머지는 본문에 병기한다.
 */
public enum AlertIntensity {
	JACKPOT,
	SPECIAL,
	TARGET,
	GOOD,
	NONE
}
