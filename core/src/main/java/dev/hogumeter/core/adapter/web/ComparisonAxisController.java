package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.DefineComparisonAxesUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * USED-05 AC-17 ① 비교축 정의 REST. {@code PUT}이지만 <b>추가 전용</b>이다 — 이미 정의된 축은 이 호출로
 * 지워지지 않는다(docs/91 참조: 삭제는 FK·데이터 유실 위험이 있어 별도 엔드포인트로 분리).
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/comparison-axes")
public class ComparisonAxisController {

	private final DefineComparisonAxesUseCase useCase;

	public ComparisonAxisController(DefineComparisonAxesUseCase useCase) {
		this.useCase = useCase;
	}

	@PutMapping
	public List<AxisView> define(@PathVariable long productId, @RequestBody AxesRequest req) {
		return useCase.ensure(productId, req.namesOrEmpty()).stream()
				.map(a -> new AxisView(a.getId(), a.getName()))
				.toList();
	}

	public record AxesRequest(List<String> names) {
		/** BE-13(코드리뷰 20260806) — JSON에 {@code names}가 없으면 null. NPE 대신 빈 목록으로 정규화. */
		public List<String> namesOrEmpty() {
			return names != null ? names : List.of();
		}
	}

	public record AxisView(long id, String name) {
	}
}
