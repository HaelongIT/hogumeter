package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.domain.alert.AlertIntensity;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealTags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AL-05 알림 본문 조립 — 순수(IO 없음). {@link AlertMessage}의 도메인 값을 사람이 읽는 문자열로 바꾼다.
 * 스텁·텔레그램 어댑터가 공유한다(전송 방식과 무관하게 본문은 하나다).
 *
 * <p><b>정직성은 여기서도 강제된다</b>(절대 원칙 1). SPARSE/NONE이면 기준가 금액을 짓지 않는다 — {@code
 * BenchmarkView.benchmarkPrice}가 이미 null이라 "표본 N건 참고"로 대신한다. 조건 태그(Q-46 ①)를 본문에
 * 실어 "왜 싸 보이는가"를 밝힌다 — {@code 배송비미상}이면 실 결제가가 표시가보다 높을 수 있음을 덧붙인다.
 * 제품/variant 이름이 없으면 "대상 미상"으로 그린다(지어내지 않는다).
 */
public class AlertMessageFormatter {

	public String format(AlertMessage m) {
		return m.followUpKind() == null ? formatFirst(m) : formatFollowUp(m);
	}

	private String formatFirst(AlertMessage m) {
		DealEvent deal = m.deal();
		List<String> lines = new ArrayList<>();
		lines.add(intensityHeadline(m.decision().intensity()));
		lines.add(subject(m));
		lines.add(won(deal.priceFirst()) + "원");
		lines.add(comparisonLine(deal.priceFirst(), m.view()));
		lines.add(verificationLine(m.view()));
		lines.add(alsoLine(m));
		lines.add(conditionLine(deal));
		lines.add(deal.sourceUrl());
		return join(lines);
	}

	private String formatFollowUp(AlertMessage m) {
		DealEvent deal = m.deal();
		List<String> lines = new ArrayList<>();
		lines.add(followUpHeadline(m));
		lines.add(subject(m));
		// PRICE_CHANGED는 최신가를 병기한다(AL-04). 나머지는 마지막 관측가.
		lines.add((m.followUpKind() == dev.hogumeter.core.domain.alert.FollowUpKind.PRICE_CHANGED
				? "최신가 " : "") + won(deal.priceLast()) + "원");
		lines.add(conditionLine(deal));
		lines.add(deal.sourceUrl());
		return join(lines);
	}

	private static String intensityHeadline(AlertIntensity intensity) {
		return switch (intensity) {
			case JACKPOT -> "🔥 역대급 특가";
			case SPECIAL -> "🟢 특가";
			case TARGET -> "🎯 목표가 이하";
			case GOOD -> "🟢 괜찮은 딜";
			case PAID_PRICE -> "🛒 내 구매가보다 쌈";
			case NONE -> "알림"; // shouldAlert=false면 여기 안 온다(방어)
		};
	}

	private static String followUpHeadline(AlertMessage m) {
		return switch (m.followUpKind()) {
			case VERIFIED -> "✅ 교차검증됨";
			case PRICE_CHANGED -> "🔁 가격 변동";
			case ENDED -> "⛔ 종료됨";
		};
	}

	/** 제품명 + variant 라벨. 둘 다 없으면 "대상 미상"(지어내지 않는다, 원문 링크로 넘긴다). */
	private static String subject(AlertMessage m) {
		String product = m.productName();
		String variant = m.variantLabel();
		if (product == null && variant == null) {
			return "대상 미상";
		}
		if (product == null) {
			return variant;
		}
		return variant == null ? product : product + " " + variant;
	}

	/**
	 * 딜 가격을 기준가·역대최저에 댄다. SUFFICIENT일 때만 금액을 낸다 — SPARSE/NONE이면 benchmarkPrice가
	 * null이라 통계 대신 "표본 N건 참고"로 정직하게 말한다(절대 원칙 1).
	 */
	private static String comparisonLine(long dealPrice, BenchmarkView view) {
		if (view == null) {
			return "";
		}
		if (view.tier() == Tier.SUFFICIENT && view.benchmarkPrice() != null) {
			StringBuilder sb = new StringBuilder();
			long diff = view.benchmarkPrice() - dealPrice;
			if (diff > 0) {
				sb.append("기준가 ").append(won(view.benchmarkPrice())).append("원보다 ")
						.append(won(diff)).append("원(").append(pct(diff, view.benchmarkPrice())).append("%) 쌈");
			}
			else {
				sb.append("기준가 ").append(won(view.benchmarkPrice())).append("원");
			}
			if (view.periodLowest() != null) {
				sb.append(" · 역대최저 ").append(won(view.periodLowest().price())).append("원");
			}
			return sb.toString();
		}
		if (view.tier() == Tier.SPARSE) {
			return "표본 " + view.n() + "건뿐이라 기준가 대신 참고";
		}
		return "기준 미확립(참고용)";
	}

	private static String verificationLine(BenchmarkView view) {
		if (view == null || view.m() <= 0) {
			return "";
		}
		return "✅ " + view.m() + "개 사이트 교차검증";
	}

	/** 최고 강도 외에 함께 충족한 트리거·딱지를 병기한다(AL-02 "본문에 나머지 병기"). */
	private static String alsoLine(AlertMessage m) {
		List<String> parts = new ArrayList<>();
		for (AlertIntensity also : m.decision().alsoSatisfied()) {
			parts.add(intensityHeadline(also).replaceAll("^\\S+\\s", "")); // 아이콘 떼고 문구만
		}
		parts.addAll(m.decision().labels());
		return parts.isEmpty() ? "" : "· " + String.join(" · ", parts);
	}

	/**
	 * 조건 태그(Q-46 ①)를 밝힌다 — "왜 싸 보이는가". {@code 배송비미상}은 저장가가 하한이라 실 결제가가 더
	 * 높을 수 있음을 덧붙인다(as-posted 조건과 부류가 다르다).
	 */
	private static String conditionLine(DealEvent deal) {
		if (deal.appliedConditions().isEmpty()) {
			return "";
		}
		String tags = deal.appliedConditions().stream().sorted().reduce((a, b) -> a + " · " + b).orElse("");
		String line = "⚠️ 조건: " + tags;
		if (deal.hasCondition(DealTags.SHIPPING_UNKNOWN)) {
			line += " (실 결제가는 표시가보다 높을 수 있음)";
		}
		return line;
	}

	private static String pct(long diff, long benchmark) {
		return BigDecimal.valueOf(diff * 100).divide(BigDecimal.valueOf(benchmark), 1, RoundingMode.HALF_UP)
				.toPlainString();
	}

	private static String won(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	/** 빈 줄은 건너뛰고 줄바꿈으로 잇는다 — 없는 근거는 빈 줄로 그리지 않는다. */
	private static String join(List<String> lines) {
		return lines.stream().filter(s -> s != null && !s.isEmpty()).reduce((a, b) -> a + "\n" + b).orElse("");
	}
}
