package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.ArchivePurchaseUseCase;
import dev.hogumeter.core.application.RecordPurchaseCommand;
import dev.hogumeter.core.application.RecordPurchaseUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 구매 기록 REST(PUR). 봉투 없는 리소스 직접 반환(Q-2 잠정 확정). */
@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

	private final RecordPurchaseUseCase recordPurchase;
	private final ArchivePurchaseUseCase archivePurchase;

	public PurchaseController(RecordPurchaseUseCase recordPurchase, ArchivePurchaseUseCase archivePurchase) {
		this.recordPurchase = recordPurchase;
		this.archivePurchase = archivePurchase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PurchaseRecorded record(@RequestBody RecordPurchaseCommand command) {
		return new PurchaseRecorded(recordPurchase.record(command));
	}

	/** PUR-06 수동 아카이브 — 관찰을 접는다(🔥·목표가 억제, Q-85). */
	@PostMapping("/{purchaseId}/archive")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable long purchaseId) {
		archivePurchase.archive(purchaseId);
	}

	/** PUR-06 재활성 — 아카이브를 되돌려 관찰을 다시 연다. */
	@PostMapping("/{purchaseId}/reactivate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reactivate(@PathVariable long purchaseId) {
		archivePurchase.reactivate(purchaseId);
	}

	public record PurchaseRecorded(long purchaseId) {
	}
}
