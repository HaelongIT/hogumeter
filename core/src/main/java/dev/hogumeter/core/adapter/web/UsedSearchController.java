package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetUsedSearchesUseCase;
import dev.hogumeter.core.application.RegisterUsedSearchCommand;
import dev.hogumeter.core.application.RegisterUsedSearchCommand.BonusGroupCommand;
import dev.hogumeter.core.application.RegisterUsedSearchUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 중고 검색 등록·조회 REST(USED-01). 제품 아래 UsedSearch. 봉투 없는 리소스 직접 반환(Q-2 잠정). */
@RestController
@RequestMapping("/api/v1/products/{productId}/used-searches")
public class UsedSearchController {

	private final RegisterUsedSearchUseCase registerUsedSearch;
	private final GetUsedSearchesUseCase getUsedSearches;

	public UsedSearchController(RegisterUsedSearchUseCase registerUsedSearch,
			GetUsedSearchesUseCase getUsedSearches) {
		this.registerUsedSearch = registerUsedSearch;
		this.getUsedSearches = getUsedSearches;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsedSearchCreated create(@PathVariable long productId, @RequestBody UsedSearchRequest req) {
		long id = registerUsedSearch.register(new RegisterUsedSearchCommand(productId,
				orEmpty(req.required()), orEmpty(req.bonusGroups()), orEmpty(req.exclude()),
				req.targetPrice(), req.pollIntervalMin()));
		return new UsedSearchCreated(id);
	}

	/** 없는 제품이면 빈 목록 — 404가 아니다. variant 조회와 같은 계약(`ProductQueryController`). */
	@GetMapping
	public List<GetUsedSearchesUseCase.UsedSearchView> list(@PathVariable long productId) {
		return getUsedSearches.listForProduct(productId);
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list != null ? list : List.of();
	}

	/** productId는 경로에서 받으므로 본문엔 없다. */
	public record UsedSearchRequest(List<String> required, List<BonusGroupCommand> bonusGroups,
			List<String> exclude, Long targetPrice, Integer pollIntervalMin) {
	}

	public record UsedSearchCreated(long usedSearchId) {
	}
}
