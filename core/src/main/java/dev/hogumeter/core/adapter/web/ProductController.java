package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.SetProductArchivedUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 제품 쓰기(REG) — 조회는 {@link ProductQueryController}. Q-91: 수동 보관/복원. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

	private final SetProductArchivedUseCase setArchived;

	public ProductController(SetProductArchivedUseCase setArchived) {
		this.setArchived = setArchived;
	}

	@PostMapping("/{productId}/archive")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void archive(@PathVariable long productId) {
		setArchived.archive(productId);
	}

	@PostMapping("/{productId}/unarchive")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unarchive(@PathVariable long productId) {
		setArchived.unarchive(productId);
	}
}
