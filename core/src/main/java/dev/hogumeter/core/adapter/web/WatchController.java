package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetWatchItemsUseCase;
import dev.hogumeter.core.application.PinDealUseCase;
import dev.hogumeter.core.application.ResolvePinUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** WATCH(docs/17) REST — 활성 탭 조회 + 핀/결말(사람이 확정하는 두 갈래만). 봉투 없는 리소스(Q-2). */
@RestController
@RequestMapping("/api/v1/watch-items")
public class WatchController {

	private final GetWatchItemsUseCase getWatchItems;
	private final PinDealUseCase pinDeal;
	private final ResolvePinUseCase resolvePin;

	public WatchController(GetWatchItemsUseCase getWatchItems, PinDealUseCase pinDeal, ResolvePinUseCase resolvePin) {
		this.getWatchItems = getWatchItems;
		this.pinDeal = pinDeal;
		this.resolvePin = resolvePin;
	}

	@GetMapping
	public List<GetWatchItemsUseCase.WatchItemView> active() {
		return getWatchItems.active();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PinCreated pin(@RequestBody PinRequest request) {
		return new PinCreated(pinDeal.pin(request.dealEventId(), request.note()));
	}

	@PostMapping("/{watchItemId}/bought")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markBought(@PathVariable long watchItemId) {
		resolvePin.markBought(watchItemId);
	}

	/** 기각·해제 둘 다 여기로 온다 — 결과가 같다(docs/17). */
	@PostMapping("/{watchItemId}/drop")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void drop(@PathVariable long watchItemId) {
		resolvePin.drop(watchItemId);
	}

	public record PinRequest(long dealEventId, String note) {
	}

	public record PinCreated(long watchItemId) {
	}
}
