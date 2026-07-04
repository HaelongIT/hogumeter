package dev.hogumeter.core.domain.deal;

/** 딜 출처 — LIVE(실시간 수집) / BACKFILL(과거 소급, 교차검증 면제·표기가 그대로). V1__init.sql deal_event.origin. */
public enum Origin {
	LIVE,
	BACKFILL
}
