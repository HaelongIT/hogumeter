package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.AssembleDigestUseCase.Digest;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.application.ComputeDigestBestOpportunityUseCase.BestOpportunity;
import dev.hogumeter.core.application.ComputeDigestTransitionUseCase.DigestTransition;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import dev.hogumeter.core.application.VariantNaming.Naming;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import dev.hogumeter.core.domain.signal.SignalColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DIG-04 다이제스트 본문 조립(순수, IO 없음) — {@link Digest}(재료)를 사람이 읽는 문자열로 바꾼다.
 * {@link dev.hogumeter.core.adapter.telegram.AlertMessageFormatter}와 같은 자리: 조립은 다른 곳에서
 * 끝났고 여기는 문구만 만든다.
 *
 * <p><b>시점 규약</b>(DIG-03): 헤더 1회로 "발송 시점 현재 지식"임을 명시한다.
 *
 * <p><b>정렬·집계</b>(DIG-05 "정보 가치순 + 무변동 합산"): 이번 창에 기회·전환·관찰 중 하나라도 있는
 * variant만 개별 줄로 그리고, 나머지(완전 무변동)는 한 줄로 합산한다 — 변동 없는 열 개를 늘어놓으면
 * 정작 중요한 한 줄이 묻힌다.
 *
 * <p><b>이번 판의 한계</b>(docs/91 Q-81에 기록): ① "검토 대기 딱지"는 큐 항목→variant 연결이 아직 없어
 * 렌더링하지 않는다(지어내느니 생략). basis는 {@code demandAxisValue}가 없으면(GROUPED 등) "전체"로
 * 표기한다.
 */
public class DigestFormatter {

	public String format(Digest digest, Map<Long, Naming> names) {
		List<String> lines = new ArrayList<>();
		lines.add("📋 주간 다이제스트 (발송 시점 현재 지식 기준)");
		if (digest.isQuietWeek()) {
			lines.add("이번 주는 조용했습니다 — 가격 변동·신호 전환 없음.");
		}
		else {
			lines.addAll(variantLines(digest.variantRows(), names));
		}
		lines.add(queueLine(digest.queue()));
		return join(lines);
	}

	private static List<String> variantLines(List<VariantDigestRow> rows, Map<Long, Naming> names) {
		List<String> lines = new ArrayList<>();
		int quiet = 0;
		for (VariantDigestRow row : rows) {
			if (!hasSignal(row)) {
				quiet++;
				continue;
			}
			lines.add(variantLine(row, names.getOrDefault(row.variantId(), Naming.UNKNOWN)));
		}
		if (quiet > 0) {
			lines.add("· 나머지 " + quiet + "개 항목은 변동 없음");
		}
		return lines;
	}

	private static boolean hasSignal(VariantDigestRow row) {
		return row.bestOpportunity().isPresent() || row.observation().inWindow() > 0 || row.transition().reportable()
				|| !row.pinEvents().isEmpty();
	}

	private static String variantLine(VariantDigestRow row, Naming naming) {
		List<String> parts = new ArrayList<>();
		parts.add(subject(naming));
		row.bestOpportunity().ifPresent(op -> parts.add(opportunityPart(op)));
		if (row.transition().reportable()) {
			parts.add(transitionPart(row.transition()));
		}
		parts.add("관찰 이번 창 +" + row.observation().inWindow() + " / 누적 " + row.observation().cumulative());
		row.pinEvents().forEach(event -> parts.add(pinEventPart(event)));
		return String.join(" · ", parts);
	}

	/** ④ 핀 결말 전이 + 부활 이벤트(기각 DROPPED는 원문이 제외 — {@link PinDigestEvent}엔 애초에 없다). */
	private static String pinEventPart(PinDigestEvent event) {
		return switch (event.type()) {
			case BOUGHT -> "📌 샀어요";
			case MISSED -> "📌 놓쳤어요";
			case REVIVED -> "📌 ↩️ 다시 살아남";
		};
	}

	private static String subject(Naming naming) {
		String product = naming.productName();
		String variant = naming.variantLabel();
		if (product == null && variant == null) {
			return "대상 미상";
		}
		if (product == null) {
			return variant;
		}
		return variant == null ? product : product + " " + variant;
	}

	private static String opportunityPart(BestOpportunity op) {
		String icon = op.active() ? "🟢" : "⛔";
		String basis = op.deal().demandAxisValue() != null ? op.deal().demandAxisValue() : "전체";
		return icon + " 최고 기회 " + won(op.opportunityPrice()) + "원(basis=" + basis + ")";
	}

	private static String transitionPart(DigestTransition transition) {
		return "🔁 " + colorLabel(transition.from()) + " → " + colorLabel(transition.to());
	}

	private static String colorLabel(SignalColor color) {
		if (color == null) {
			return "-";
		}
		return switch (color) {
			case GREEN -> "🟢GREEN";
			case YELLOW -> "🟡YELLOW";
			case RED -> "🔴RED";
			case GRAY -> "⚪GRAY";
		};
	}

	/**
	 * ⑤ 전역 스톡. 대상 미상(미귀속) 건수를 병기하고, 가장 오래 놓친 항목의 "N회째 미확인"을 낸다
	 * (DIG-04). {@code digestAppearances}는 <b>지금까지 성공한</b> 발송 횟수라 이번 발송이 성공하면
	 * 그만큼 하나 늘어난다 — 그래서 +1로 이번 발송의 몫을 미리 반영한다(저장은 발송 성공 후에
	 * {@code SendDigestUseCase}가 한다, 이 함수는 문구만 만든다). 전부 첫 등장(0회)이면 "N회째"가
	 * 잡음이라 생략한다.
	 */
	private static String queueLine(List<PendingItem> queue) {
		if (queue.isEmpty()) {
			return "⑤ 검토 대기: 없음";
		}
		long unassigned = queue.stream().filter(item -> item.subject() == null).count();
		int worstStreak = queue.stream().mapToInt(item -> item.digestAppearances() + 1).max().orElse(1);
		StringBuilder line = new StringBuilder("⑤ 검토 대기 ").append(queue.size()).append("건");
		if (unassigned > 0) {
			line.append("(대상 미상 ").append(unassigned).append("건 포함)");
		}
		if (worstStreak > 1) {
			line.append(" · 최다 ").append(worstStreak).append("회째 미확인");
		}
		return line.toString();
	}

	private static String won(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	private static String join(List<String> lines) {
		return lines.stream().filter(s -> s != null && !s.isEmpty()).reduce((a, b) -> a + "\n" + b).orElse("");
	}
}
