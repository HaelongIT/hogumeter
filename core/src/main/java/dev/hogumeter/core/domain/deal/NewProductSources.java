package dev.hogumeter.core.domain.deal;

import java.util.Locale;
import java.util.Set;

/**
 * 신품 기준가에 들어올 수 있는 수집 소스의 <b>허용집합</b>(BM-02).
 *
 * <p>왜 필요한가: collector는 핫딜 게시판과 중고 마켓(번개장터)을 <b>같은 {@code raw_deal_post}</b>에
 * 적재한다(둘 다 {@code ParsedDeal}이다). core의 적재는 {@code findUnprocessed()}로 소스를 가리지 않고
 * 전부 읽으므로, 번개를 켜는 순간 700,000원짜리 중고 아이폰이 신품 딜이 되어 기준가를 끌어내린다.
 * M2(중고)의 첫 걸음이 번개를 켜는 것이므로 그 전에 이 경계를 잠근다.
 *
 * <p><b>차단이 아니라 허용으로 판정한다.</b> "중고 사이트 목록"을 차단하는 방식은 부분적으로 아는
 * 패턴이라 새 중고 사이트(중고나라 등)를 만나면 규칙이 매치 실패하고 <b>하류의 기본값</b>(=신품으로 적재)
 * 으로 조용히 떨어진다. 반대로 신품 게시판만 허용하면 모르는 소스는 실패해도 안전한 쪽으로 떨어진다.
 *
 * <p><b>대신 놓친 사실을 실어 보낸다.</b> 허용집합의 대가는 "새 게시판을 추가했는데 core가 몰라서
 * 조용히 버린다"이다 — 그래서 {@code IngestReport.skippedForeignSource}가 그 수를 세고 틱 리포트에
 * 실린다. 그 카운터가 오르는 것이 곧 이 목록과 collector 레지스트리가 어긋났다는 신호다.
 */
public final class NewProductSources {

	/**
	 * 신품 핫딜 게시판. collector의 BOARD 레지스트리와 <b>같은 어휘</b>다.
	 *
	 * <p>실제 폴링 대상은 robots 실측에 따라 좁아질 수 있으나(2026-07-22 현재 뽐뿌 1사 — 루리웹·펨코는
	 * {@code robots.txt}가 금지) 여기서는 <b>줄이지 않는다.</b> 이 집합은 "폴링하는 곳"이 아니라
	 * "신품으로 해석해도 되는 곳"이고, 그 성격은 폴링 여부와 무관하기 때문이다. 되살릴 때 core를 같이
	 * 고쳐야 한다면 그 사이 딜이 조용히 버려진다.
	 */
	private static final Set<String> BOARDS = Set.of("ppomppu", "ruliweb", "fmkorea");

	private NewProductSources() {
	}

	/** 이 소스의 게시물을 신품 딜(deal_event)로 해석해도 되는가. 모르면 {@code false}(안전한 쪽). */
	public static boolean acceptsAsNewProduct(String site) {
		return site != null && BOARDS.contains(site.trim().toLowerCase(Locale.ROOT));
	}

	/** 허용집합 자체(게이트·테스트용). 정렬·수정 불가. */
	public static Set<String> boards() {
		return BOARDS;
	}
}
