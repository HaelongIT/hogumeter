package dev.hogumeter.core.domain.signal;

/** SIG 신호등 색(docs/16 SIG-01). GREEN(P25↓ 활성 딜)·YELLOW(기준가↓)·RED(없음)·GRAY(SPARSE/NONE). */
public enum SignalColor {
	GREEN,
	YELLOW,
	RED,
	GRAY
}
