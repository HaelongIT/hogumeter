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

	@GetMapping("/api/v1/variants/{variantId}/benchmark")
	public BenchmarkView benchmark(@PathVariable long variantId,
			@RequestParam int periodMonths,
			@RequestParam(defaultValue = "false") boolean includeOutliers) {
		return getBenchmark.getBenchmark(variantId, periodMonths, includeOutliers);
	}
}
