package dev.hogumeter.core.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 인라인 버튼 콜백(Q-15 승격·기각을 텔레그램에서)의 순수 라우팅. {@code callback_data}("promote:123")를
 * 파싱해 {@link ResolveReviewItemUseCase}로 넘긴다. 폴러가 받은 콜백을 이 라우터로 보내고, 반환 문구를
 * 버튼 누른 사람에게 답한다.
 *
 * <p><b>SEC-03(인바운드 화이트리스트)</b>: 허용된 chat_id의 명령만 처리한다 — 토큰만 알면 누구든 봇에게
 * 명령을 보낼 수 있으므로. 허용 목록은 {@code telegram.allowed-chat-ids}, 비면 {@code telegram.chat-id}(아웃바운드
 * 대상 = 신뢰된 사용자)로 폴백한다. <b>둘 다 비면 아무도 허용하지 않는다</b>(닫힌 기본값 — 열려면 명시해야 한다).
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class ReviewCallbackRouter {

	private static final Logger log = LoggerFactory.getLogger(ReviewCallbackRouter.class);

	private final ResolveReviewItemUseCase resolve;
	private final IgnoreDealUseCase ignoreDeal;
	private final Set<Long> allowedChats;

	@org.springframework.beans.factory.annotation.Autowired
	public ReviewCallbackRouter(ResolveReviewItemUseCase resolve, IgnoreDealUseCase ignoreDeal,
			@Value("${telegram.allowed-chat-ids:}") String allowedCsv, @Value("${telegram.chat-id:}") String chatId) {
		this.resolve = resolve;
		this.ignoreDeal = ignoreDeal;
		this.allowedChats = parseChats(allowedCsv.isBlank() ? chatId : allowedCsv);
	}

	/** 테스트 seam — 허용 목록을 직접 준다. */
	ReviewCallbackRouter(ResolveReviewItemUseCase resolve, IgnoreDealUseCase ignoreDeal, Set<Long> allowedChats) {
		this.resolve = resolve;
		this.ignoreDeal = ignoreDeal;
		this.allowedChats = allowedChats;
	}

	/**
	 * @return 버튼 누른 사람에게 answerCallbackQuery로 답할 짧은 문구. 실패도 문구로 돌려준다(던지지 않는다) —
	 *     인바운드 한 건의 오류가 폴러를 죽이지 않게.
	 */
	public String route(long fromChatId, String callbackData) {
		if (!allowedChats.contains(fromChatId)) {
			log.warn("SEC-03: 허용되지 않은 chat {}의 명령을 거부했습니다", fromChatId);
			return "권한이 없습니다.";
		}
		String[] parts = callbackData == null ? new String[0] : callbackData.split(":", 2);
		if (parts.length != 2) {
			return "알 수 없는 명령입니다.";
		}
		long id;
		try {
			id = Long.parseLong(parts[1]);
		}
		catch (NumberFormatException e) {
			return "알 수 없는 명령입니다.";
		}
		try {
			switch (parts[0]) {
				case "promote" -> {
					resolve.promote(id, "TELEGRAM");
					return "승격했습니다 — 표본에 복귀합니다.";
				}
				case "reject" -> {
					resolve.reject(id, "TELEGRAM");
					return "기각했습니다 — 영구 제외합니다.";
				}
				case "ignore" -> {
					ignoreDeal.ignore(id); // Q-22 사후학습 — 노이즈로 기록, 빈출 토큰을 제외 키워드 후보로
					return "무시했습니다 — 비슷한 알림이 잦으면 제외 키워드를 제안합니다.";
				}
				default -> {
					return "알 수 없는 명령입니다.";
				}
			}
		}
		catch (ReviewItemNotFoundException e) {
			return "이미 처리됐거나 없는 항목입니다.";
		}
		catch (UnclassifiedPromoteNotSupportedException e) {
			return "미상 항목은 승격할 수 없습니다(대상 지정 필요).";
		}
	}

	private static Set<Long> parseChats(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(Long::parseLong)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
