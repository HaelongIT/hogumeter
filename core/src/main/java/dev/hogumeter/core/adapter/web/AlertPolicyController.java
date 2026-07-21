package dev.hogumeter.core.adapter.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hogumeter.core.application.AlertPolicySettingsUseCase;
import dev.hogumeter.core.domain.alert.AlertPolicySettings;
import dev.hogumeter.core.domain.alert.InvalidAlertPolicyException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REG-03 알림 정책 설정 REST. 봉투 없는 리소스 직접 반환(Q-2 확정).
 *
 * <p>목표가·기간·조용시간에 더해 {@code k_display}(Q-48 ①)·{@code exclude_keywords}(Q-28)까지 다룬다 —
 * 둘 다 이제 엔티티가 매핑하고 사용자 손잡이로 살아 있다. {@code demand_axis_filter}·⚠️라벨 모드 토글은
 * 소비 기능과 함께 매핑을 확장한다(docs/91 Q-66).
 */
@RestController
public class AlertPolicyController {

	private static final String PATH = "/api/v1/variants/{variantId}/alert-policy";

	private final AlertPolicySettingsUseCase settings;

	public AlertPolicyController(AlertPolicySettingsUseCase settings) {
		this.settings = settings;
	}

	@GetMapping(PATH)
	public AlertPolicyView get(@PathVariable long variantId) {
		return settings.get(variantId).map(AlertPolicyView::of).orElseGet(AlertPolicyView::unconfigured);
	}

	@PutMapping(PATH)
	public AlertPolicyView put(@PathVariable long variantId, @RequestBody UpdateRequest request) {
		return AlertPolicyView.of(settings.update(variantId, request.toSettings()));
	}

	/**
	 * 미설정도 200이다 — 404로 내면 화면이 "정책이 없다"와 "variant를 못 찾았다"를 구분하지 못한다.
	 *
	 * <p>미설정일 때 {@code periodMonths}는 <b>null</b>이다. 알림 판정이 쓰는 기본값(6개월)은
	 * {@code EvaluateAlertOnDealUseCase}의 private 상수라 여기서 읽을 수 없다. 지어내서 채우면
	 * 그 값이 곧 세 번째 사본이 되고, 사본은 드리프트한다(docs/91 Q-48).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record AlertPolicyView(boolean configured, Long targetPrice, Integer periodMonths,
			Integer quietHoursStart, Integer quietHoursEnd, Integer kDisplay, List<String> excludeKeywords) {

		static AlertPolicyView of(AlertPolicySettings settings) {
			return new AlertPolicyView(true, settings.targetPrice(), settings.periodMonths(),
					settings.quietHoursStart(), settings.quietHoursEnd(), settings.kDisplay(),
					settings.excludeKeywords());
		}

		/**
		 * 미설정이라도 {@code kDisplay}는 <b>숫자로 말한다</b> — 기본값의 정본이
		 * {@code AlertPolicySettings.DEFAULT_K_DISPLAY} 한 곳이라 여기서 읽어도 사본이 생기지 않는다.
		 * (기간 P는 아직 그 정본이 없어 null이다 — Q-48 ②.) 제외 키워드는 미설정이면 빈 목록이다.
		 */
		static AlertPolicyView unconfigured() {
			return new AlertPolicyView(false, null, null, null, null, AlertPolicySettings.DEFAULT_K_DISPLAY,
					List.of());
		}
	}

	/** 래퍼 record — JSON의 누락 필드가 {@code int} 언박싱 NPE(500)가 되지 않게 전부 박싱 타입으로 받는다. */
	public record UpdateRequest(Long targetPrice, Integer periodMonths, Integer quietHoursStart,
			Integer quietHoursEnd, Integer kDisplay, List<String> excludeKeywords) {

		AlertPolicySettings toSettings() {
			if (periodMonths == null) {
				throw new InvalidAlertPolicyException("periodMonths is required");
			}
			// K·제외 키워드는 선택이다 — 안 보내면 기본값/빈 목록. 정규화(공백 제거·중복 접기)는 AlertPolicySettings가 한다.
			return new AlertPolicySettings(targetPrice, periodMonths, quietHoursStart, quietHoursEnd,
					kDisplay == null ? AlertPolicySettings.DEFAULT_K_DISPLAY : kDisplay,
					excludeKeywords == null ? List.of() : excludeKeywords);
		}
	}
}
