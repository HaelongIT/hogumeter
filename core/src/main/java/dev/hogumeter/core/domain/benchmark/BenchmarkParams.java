package dev.hogumeter.core.domain.benchmark;

import java.math.BigDecimal;

/**
 * 기준가 엔진의 유일한 기명상수 seam(docs/benchmark/01 line 25) — 수치 파라미터를 한 곳에 격리.
 * 도메인 로직은 하드코딩 대신 주입받은 이 객체만 참조한다(테스트는 명시 주입, docs/91 Q-1).
 * K_FILL은 파생 불변식(시스템 고정). 값 근거는 docs/31-detailed-params.md(2026-07-04 승인).
 *
 * @param mergePriceToleranceRatio 병합 가격 허용 비율(±α) — BM-04
 * @param mergePriceToleranceFloorWon 병합 가격 허용 절대 하한(원) — BM-04
 * @param mergeWindowHours 병합 시간 윈도우 — BM-04
 * @param outlierIqrMultiplier 이상치 IQR 배수(Tukey) — BM-05
 * @param coldStartJackpotRatio NONE 구간 대박딜 폴백 문턱(현재가 대비 ↓ 비율) — BM-06 AC-4
 * @param kDisplay 기준가 라벨 임계(사용자 손잡이, 3~10) — BM-06
 * @param expandLimitMonths 과거 자동확장 상한(개월, 시스템 고정) — BM-06 AC-5
 */
public record BenchmarkParams(
		BigDecimal mergePriceToleranceRatio,
		long mergePriceToleranceFloorWon,
		int mergeWindowHours,
		BigDecimal outlierIqrMultiplier,
		BigDecimal coldStartJackpotRatio,
		int kDisplay,
		int expandLimitMonths) {

	public BenchmarkParams {
		if (kDisplay < 3 || kDisplay > 10) {
			throw new IllegalArgumentException("kDisplay must be in 3..10 (V1 alert_policy CHECK): " + kDisplay);
		}
	}

	/** 표본 채움 목표 = max(7, K_DISPLAY+2). 불변식 K_FILL &gt; K_DISPLAY를 3~10 전 구간 보존. */
	public int kFill() {
		return Math.max(7, kDisplay + 2);
	}

	/** docs/31 승인값(2026-07-04). 어댑터·기본 생성 지점 전용 — 도메인 로직은 주입값을 쓴다. */
	public static BenchmarkParams defaults() {
		return new BenchmarkParams(
				new BigDecimal("0.02"),
				5_000L,
				48,
				new BigDecimal("1.5"),
				new BigDecimal("0.30"),
				5,
				12);
	}
}
