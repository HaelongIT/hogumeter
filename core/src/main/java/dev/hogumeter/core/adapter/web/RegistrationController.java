package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.RegisterProductCommand;
import dev.hogumeter.core.application.RegisterProductUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 제품 등록 REST(REG). 봉투 없는 리소스 직접 반환(Q-2 잠정 확정). */
@RestController
@RequestMapping("/api/v1/products")
public class RegistrationController {

	private final RegisterProductUseCase registerProduct;

	public RegistrationController(RegisterProductUseCase registerProduct) {
		this.registerProduct = registerProduct;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductCreated register(@RequestBody RegisterProductCommand command) {
		return new ProductCreated(registerProduct.register(command));
	}

	public record ProductCreated(long productId) {
	}
}
