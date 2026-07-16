package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetBenchmarkUseCase;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기준가 조회 REST(BM-06). BenchmarkView 계약 그대로 반환(docs/benchmark/03). */
@RestController
public class BenchmarkController {

	private final GetBenchmarkUseCase getBenchmark;

	public BenchmarkController(GetBenchmarkUseCase getBenchmark) {
		this.getBenchmark = getBenchmark;
	}

	/**
	 * @param demandAxisValue 분리(SPLIT) 제품에서 볼 수요축 값(Q-66 ①). 묶음이면 없어도 되고 보내도 무시한다.
	 *     분리인데 없으면 400 — 전체 딜로 답하면 그게 묶음의 거짓말이다.
	 */
	@GetMapping("/api/v1/variants/{variantId}/benchmark")
	public BenchmarkView benchmark(@PathVariable long variantId,
			@RequestParam int periodMonths,
			@RequestParam(defaultValue = "false") boolean includeOutliers,
			@RequestParam(required = false) String demandAxisValue) {
		return getBenchmark.getBenchmark(variantId, periodMonths, includeOutliers, demandAxisValue);
	}
}
