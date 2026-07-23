package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.CoupangObservationCommand;
import dev.hogumeter.core.application.GetLatestCoupangPriceUseCase;
import dev.hogumeter.core.application.IngestCoupangObservationUseCase;
import dev.hogumeter.core.domain.comparison.FixedWindowRateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/**
 * CMP-02 쿠팡 크롬 확장 ingest REST(SEC-04: 고정 토큰 인증 + 스키마 검증 + 레이트리밋). <b>서버는 쿠팡에
 * 절대 접근하지 않는다</b> — 확장이 사용자 브라우저에서 읽어 보낸 값만 받는다.
 *
 * <p>토큰 미설정({@code EXTENSION_INGEST_TOKEN} 빈 값)이면 <b>전부 거절</b>한다 — "설정을 지웠는데
 * 게이트가 초록"이 가장 나쁘다는 원칙과 같은 이유로, 미설정을 "인증 없음(열림)"으로 다루지 않는다.
 */
@RestController
@RequestMapping("/api/v1/coupang")
public class CoupangObservationController {

	private final IngestCoupangObservationUseCase ingest;
	private final GetLatestCoupangPriceUseCase getLatest;
	private final String configuredToken;
	private final Clock clock;
	private final FixedWindowRateLimiter rateLimiter;

	public CoupangObservationController(IngestCoupangObservationUseCase ingest,
			GetLatestCoupangPriceUseCase getLatest, @Value("${extension.ingest-token:}") String configuredToken,
			Clock clock) {
		this.ingest = ingest;
		this.getLatest = getLatest;
		this.configuredToken = configuredToken;
		this.clock = clock;
		this.rateLimiter = new FixedWindowRateLimiter(RATE_LIMIT_PER_MINUTE);
	}

	// docs/91 Q-78 잠정 — 실사용(확장이 페이지 전환마다 1건씩 보내는 빈도)으로 재조정.
	private static final int RATE_LIMIT_PER_MINUTE = 30;

	@PostMapping("/observations")
	@ResponseStatus(HttpStatus.CREATED)
	public ObservationCreated ingest(@RequestHeader(value = "X-Extension-Token", required = false) String token,
			@RequestBody ObservationRequest req) {
		authenticate(token);
		if (!rateLimiter.tryAcquire(clock.instant())) {
			throw new RateLimitExceededException();
		}
		Instant observedAt = req.observedAt() != null ? req.observedAt() : clock.instant();
		long id = ingest.ingest(new CoupangObservationCommand(req.variantId(), req.regularPrice(), req.wowPrice(),
				req.shippingFee(), req.url(), observedAt));
		return new ObservationCreated(id);
	}

	@GetMapping("/variants/{variantId}/latest-price")
	public LatestPriceResponse latest(@PathVariable long variantId) {
		return getLatest.get(variantId)
				.map(v -> new LatestPriceResponse(v.regularPrice(), v.wowPrice(), v.shippingFee(), v.url(),
						v.observedAt()))
				.orElse(new LatestPriceResponse(null, null, null, null, null));
	}

	/** 상수시간 비교 — 단일 사용자·저가치 표적이라 과설계는 아니지만 거의 공짜라 넣는다. */
	private void authenticate(String token) {
		if (configuredToken.isBlank() || token == null
				|| !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
						configuredToken.getBytes(StandardCharsets.UTF_8))) {
			throw new ExtensionAuthException();
		}
	}

	public record ObservationRequest(long variantId, long regularPrice, Long wowPrice, Long shippingFee, String url,
			Instant observedAt) {
	}

	public record ObservationCreated(long observationId) {
	}

	/** 관측이 없으면 전 필드 null — 지어내지 않는다. */
	public record LatestPriceResponse(Long regularPrice, Long wowPrice, Long shippingFee, String url,
			Instant observedAt) {
	}
}
