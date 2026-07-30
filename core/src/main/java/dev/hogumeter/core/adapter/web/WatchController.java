package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.AcknowledgeRevivalUseCase;
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
	private final AcknowledgeRevivalUseCase acknowledgeRevival;

	public WatchController(GetWatchItemsUseCase getWatchItems, PinDealUseCase pinDeal, ResolvePinUseCase resolvePin,
			AcknowledgeRevivalUseCase acknowledgeRevival) {
		this.getWatchItems = getWatchItems;
		this.pinDeal = pinDeal;
		this.resolvePin = resolvePin;
		this.acknowledgeRevival = acknowledgeRevival;
	}

	@GetMapping
	public List<GetWatchItemsUseCase.WatchItemView> active() {
		return getWatchItems.active();
	}

	/** 회고 탭 — 결말(BOUGHT·MISSED·DROPPED)에 닿은 핀을 최근 결말 순으로. */
	@GetMapping("/resolved")
	public List<GetWatchItemsUseCase.WatchItemView> resolved() {
		return getWatchItems.resolved();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PinCreated pin(@RequestBody PinRequest request) {
		return new PinCreated(pinDeal.pin(request.dealEventId(), request.note()));
	}

	/** PUR 프리필(Q-83 ②) 재료를 봉투 없이 직접 반환(Q-2) — web이 판단 화면 폼을 채운다. */
	@PostMapping("/{watchItemId}/bought")
	public ResolvePinUseCase.BoughtPrefill markBought(@PathVariable long watchItemId) {
		return resolvePin.markBought(watchItemId);
	}

	/** 기각·해제 둘 다 여기로 온다 — 결과가 같다(docs/17). */
	@PostMapping("/{watchItemId}/drop")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void drop(@PathVariable long watchItemId) {
		resolvePin.drop(watchItemId);
	}

	/** 부활 미응답 플래그 확인(Q-83 ⑤) — 핀 상태 전이는 없다, 플래그만 내린다. */
	@PostMapping("/{watchItemId}/acknowledge-revival")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void acknowledgeRevival(@PathVariable long watchItemId) {
		acknowledgeRevival.acknowledge(watchItemId);
	}

	public record PinRequest(long dealEventId, String note) {
	}

	public record PinCreated(long watchItemId) {
	}
}
