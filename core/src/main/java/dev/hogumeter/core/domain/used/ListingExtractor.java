package dev.hogumeter.core.domain.used;

import java.util.Optional;

/**
 * USED-04 AC-15 — 추출은 <b>인터페이스 뒤에</b> 둔다. v1은 규칙 기반이고 v2에서 LLM으로 교체 가능하다.
 * 이 경계를 처음부터 두는 이유는, 나중에 끼우려면 호출부가 규칙 추출의 한계에 맞춰 굳기 때문이다.
 *
 * <p>못 읽으면 <b>빈 Optional</b>이다. 0원·빈 제목 같은 sentinel을 내지 않는다 — 그 값은 가격 맥락과
 * 위험 신호를 통과해 "100% 싸다" 같은 정상 응답 모양의 거짓말이 된다(Q-53의 교훈).
 */
public interface ListingExtractor {

	Optional<ExtractedListing> extract(EvaluationInput input);
}
