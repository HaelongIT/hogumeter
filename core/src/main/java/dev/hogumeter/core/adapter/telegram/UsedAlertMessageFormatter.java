package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.UsedAlertMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * USED-03 중고 알림 본문 조립 — 순수(IO 없음). 스텁·텔레그램 어댑터가 공유한다(전송 방식과 무관하게
 * 본문은 하나다). 신품 알림과 포맷터를 나눈 이유는 재료가 다르기 때문이다 — 여긴 기준가·강도가 없다.
 *
 * <p><b>판정하지 않는다</b>(절대 원칙 2, AC-14). 소실은 "판매완료"가 아니라 <b>추정</b>으로 적고
 * 원문 확인을 권한다 — 번개의 상태 코드표가 미실측이라(Q-44) 예약중·숨김도 소실로 보일 수 있다.
 * 제품 이름을 못 찾으면 "대상 미상"으로 그린다(지어내지 않는다). URL이 없으면 그 줄을 생략한다.
 */
public class UsedAlertMessageFormatter {

	public String format(UsedAlertMessage m) {
		List<String> lines = new ArrayList<>();
		lines.add(headline(m));
		lines.add(subject(m));
		lines.add(m.title());
		lines.add(priceLine(m));
		lines.add(m.url());
		return join(lines);
	}

	private static String headline(UsedAlertMessage m) {
		return switch (m.kind()) {
			case NEW -> "🆕 중고 신규 매물";
			case PRICE_DROP -> "📉 중고 가격 하락";
			// 목록에서 사라졌다는 사실만 말한다. 팔렸는지·숨겼는지·예약인지는 우리가 모른다.
			case SOLD_OUT -> "⛔ 중고 매물 소실 (판매완료 추정 — 원문 확인 필요)";
		};
	}

	private static String subject(UsedAlertMessage m) {
		return (m.productName() == null || m.productName().isBlank()) ? "대상 미상" : m.productName();
	}

	private static String priceLine(UsedAlertMessage m) {
		if (m.kind() == UsedAlertMessage.UsedAlertKind.PRICE_DROP && m.previousPrice() != null) {
			return won(m.previousPrice()) + "원 → " + won(m.price()) + "원";
		}
		return won(m.price()) + "원";
	}

	private static String won(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	/** 빈 줄은 건너뛰고 줄바꿈으로 잇는다 — 없는 근거는 빈 줄로 그리지 않는다. */
	private static String join(List<String> lines) {
		return lines.stream().filter(s -> s != null && !s.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
	}
}
