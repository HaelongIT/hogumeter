package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GlobalExcludeKeywords;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 설정 REST(Q-28 ①) — 지금은 <b>전역 제외 키워드</b> 하나다. 모든 variant에 함께 적용되는 노이즈
 * 키워드(리퍼·중고 등)를 한 곳에서 본다·고친다.
 *
 * <p><b>PUT은 전체 교체</b>다(부분 갱신 없음) — 화면이 목록 전체를 보내고 그대로 저장한다. 정책 패널의
 * per-product 제외 키워드와 같은 계약이라 클라이언트가 두 규칙을 기억하지 않아도 된다.
 */
@RestController
@RequestMapping("/api/v1/settings")
public class GlobalSettingsController {

	private final GlobalExcludeKeywords globalKeywords;

	public GlobalSettingsController(GlobalExcludeKeywords globalKeywords) {
		this.globalKeywords = globalKeywords;
	}

	@GetMapping("/exclude-keywords")
	public View get() {
		return new View(globalKeywords.keywords());
	}

	/** @return 저장된(정규화된) 목록 — 클라이언트가 무엇이 실제로 저장됐는지 되돌려 받는다. */
	@PutMapping("/exclude-keywords")
	public View put(@RequestBody UpdateRequest request) {
		return new View(globalKeywords.replace(request.excludeKeywords()));
	}

	/** 미설정이면 빈 목록 — "제외 없음"을 그대로 낸다. */
	public record View(List<String> excludeKeywords) {
	}

	public record UpdateRequest(List<String> excludeKeywords) {
	}
}
