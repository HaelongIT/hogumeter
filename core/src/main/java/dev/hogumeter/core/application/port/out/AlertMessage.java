package dev.hogumeter.core.application.port.out;

import dev.hogumeter.core.domain.alert.AlertDecision;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.deal.DealEvent;

/**
 * 발송할 알림의 재료(포트 DTO). 어댑터(텔레그램)가 강도 아이콘·가격·갭·검증상태·딱지·원문 링크로 포맷한다.
 * 메시지 문자열 조립은 어댑터 책임 — 도메인은 무엇을 보낼지(deal·view·decision)만 넘긴다.
 *
 * <p>{@code followUpKind}: null = 첫(원) 알림(view·decision을 채운다). 아니면 후속 알림(AL-03,
 * VERIFIED·PRICE_CHANGED·ENDED) — 후속은 기준가 판정이 아니라 전이 통지라 view·decision이 null일 수 있다.
 * 어댑터가 이 값으로 포맷을 가른다.
 */
public record AlertMessage(DealEvent deal, BenchmarkView view, AlertDecision decision, FollowUpKind followUpKind) {
}
