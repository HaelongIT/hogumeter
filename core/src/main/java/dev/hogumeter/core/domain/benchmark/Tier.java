package dev.hogumeter.core.domain.benchmark;

/**
 * 표본 3단 판정. SUFFICIENT(n ≥ K_DISPLAY, 정식 산출) / SPARSE(1~4, 통계 용어 금지) / NONE(n=0).
 * docs/benchmark/00 line 23, BM-06 AC-1/3/4.
 */
public enum Tier {
	SUFFICIENT,
	SPARSE,
	NONE
}
