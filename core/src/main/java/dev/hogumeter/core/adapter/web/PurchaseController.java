package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.RecordPurchaseCommand;
import dev.hogumeter.core.application.RecordPurchaseUseCase;
import org.springframework.http.HttpStatus;
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

	public PurchaseController(RecordPurchaseUseCase recordPurchase) {
		this.recordPurchase = recordPurchase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PurchaseRecorded record(@RequestBody RecordPurchaseCommand command) {
		return new PurchaseRecorded(recordPurchase.record(command));
	}

	public record PurchaseRecorded(long purchaseId) {
	}
}
