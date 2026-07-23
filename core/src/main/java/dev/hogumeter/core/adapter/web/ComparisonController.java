package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetComparisonUseCase;
import dev.hogumeter.core.application.GetComparisonUseCase.ComparisonRow;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** USED-05 AC-18 병렬 비교표 조회 REST. 렌더링(빈칸 체크리스트·정렬)은 web의 몫 — 여기는 데이터만. */
@RestController
@RequestMapping("/api/v1/products/{productId}/comparison")
public class ComparisonController {

	private final GetComparisonUseCase useCase;

	public ComparisonController(GetComparisonUseCase useCase) {
		this.useCase = useCase;
	}

	@GetMapping
	public ComparisonResponse get(@PathVariable long productId) {
		GetComparisonUseCase.ComparisonView view = useCase.get(productId);
		List<AxisView> axes = view.axes().stream().map(a -> new AxisView(a.getId(), a.getName())).toList();
		List<RowView> rows = view.rows().stream().map(RowView::from).toList();
		return new ComparisonResponse(axes, rows);
	}

	public record ComparisonResponse(List<AxisView> axes, List<RowView> rows) {
	}

	public record AxisView(long id, String name) {
	}

	/** {@code axisValues}는 승격 안 된 축의 키를 아예 안 낸다 — "미확인"과 "빈 값"을 혼동하지 않게. */
	public record RowView(long listingId, String title, long price, String url, Map<Long, String> axisValues,
			List<String> notes) {

		static RowView from(ComparisonRow row) {
			return new RowView(row.listingId(), row.title(), row.price(), row.url(), row.axisValues(),
					row.notes());
		}
	}
}
