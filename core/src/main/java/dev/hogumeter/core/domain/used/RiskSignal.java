package dev.hogumeter.core.domain.used;

/**
 * 위험 신호 한 건(USED-04 AC-13·14). <b>나열용 사실</b>이지 판정이 아니다 — "사기다"·"위험하다" 같은
 * 결론 문구를 담지 않는다(절대 원칙 2: 판단은 사람). category=신호 부류, detail=근거 표현.
 */
public record RiskSignal(String category, String detail) {
}
