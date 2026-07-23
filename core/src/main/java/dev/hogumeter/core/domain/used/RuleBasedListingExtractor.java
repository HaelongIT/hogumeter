package dev.hogumeter.core.domain.used;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * USED-04 v1 규칙 추출기(순수, IO 0). 붙여넣은 본문에서 제목·가격·링크를 읽는다.
 *
 * <p><b>단위 없는 숫자는 가격이 아니다.</b> 중고 글에는 모델명(`RTX 5070`)·용량(`256 기가`)·연식이
 * 섞여 있어, 맨숫자를 가격으로 읽으면 조용히 틀린 맥락을 그린다 — 실제로 collector 파서가 `RTX 5070`을
 * 5,070원으로 읽은 적이 있다. 그래서 <b>`원`·`만원`이 붙은 것만</b> 가격으로 본다.
 *
 * <p><b>가격 어휘 해석이 collector(`pipeline/price.py`)와 갈릴 위험</b>은 인정하고 좁게 둔다: 여기는
 * 목록 HTML이 아니라 사람이 붙여넣은 자유 본문이고, 못 읽으면 <b>수동 입력으로 폴백</b>한다(AC-12 ③).
 * 규칙을 넓히고 싶어지면 그건 v2(LLM) 신호다 — 두 모듈에 같은 어휘를 각자 키우지 않는다(docs/91 Q-77).
 */
public class RuleBasedListingExtractor implements ListingExtractor {

	/** `85만원`·`85만` — 만 단위. 소수(`85.5만`)는 다루지 않는다(못 읽으면 폴백). */
	private static final Pattern MAN_WON = Pattern.compile("([0-9][0-9,]*)\\s*만\\s*원?");

	/** `850,000원`·`850000 원` — 원 단위. 단위가 <b>반드시</b> 있어야 한다. */
	private static final Pattern WON = Pattern.compile("([0-9][0-9,]*)\\s*원");

	private static final Pattern URL = Pattern.compile("https?://\\S+");

	@Override
	public Optional<ExtractedListing> extract(EvaluationInput input) {
		return switch (input.kind()) {
			case MANUAL -> fromManual(input);
			case TEXT -> fromText(input.text());
			// 실 fetch는 정지조건이라 이 추출기는 URL을 다루지 않는다. 상위가 스냅샷 조회로 대신하고,
			// 그것도 없으면 "본문을 붙여넣어 달라"로 폴백한다(AC-12 ②단).
			case URL -> Optional.empty();
		};
	}

	private static Optional<ExtractedListing> fromManual(EvaluationInput input) {
		if (input.price() == null || input.title() == null || input.title().isBlank()) {
			return Optional.empty(); // 수동 입력인데 필수 필드가 비었다 — 지어내지 않는다
		}
		return Optional.of(new ExtractedListing(input.title().trim(), input.price(), input.url()));
	}

	private static Optional<ExtractedListing> fromText(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String[] lines = text.strip().split("\\R");
		String title = lines[0].strip();
		Long price = largestAmount(text);
		if (title.isEmpty() || price == null) {
			return Optional.empty();
		}
		Matcher url = URL.matcher(text);
		return Optional.of(new ExtractedListing(title, price, url.find() ? url.group() : null));
	}

	/**
	 * 본문의 금액 중 <b>가장 큰 것</b>을 매물가로 본다. 배송비·에누리·보증금이 함께 적히는 일이 흔한데,
	 * 그중 작은 값을 고르면 "말도 안 되게 싼 매물"로 보여 위험 신호가 잘못 켜진다.
	 */
	private static Long largestAmount(String text) {
		Long best = null;
		Matcher man = MAN_WON.matcher(text);
		while (man.find()) {
			best = max(best, digits(man.group(1)) * 10_000);
		}
		// `85만원`은 WON 패턴에도 `만원`의 `원`으로 걸리지 않는다(앞에 숫자가 없으므로). 원 단위만 훑는다.
		Matcher won = WON.matcher(text);
		while (won.find()) {
			best = max(best, digits(won.group(1)));
		}
		return best;
	}

	private static Long max(Long current, long candidate) {
		return (current == null || candidate > current) ? candidate : current;
	}

	private static long digits(String raw) {
		return Long.parseLong(raw.replace(",", ""));
	}
}
