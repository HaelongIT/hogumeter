package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.DealAlreadyPinnedException;
import dev.hogumeter.core.application.DealEventNotFoundException;
import dev.hogumeter.core.application.DealNotPinnableException;
import dev.hogumeter.core.application.DemandAxisValueRequiredException;
import dev.hogumeter.core.application.DuplicatePriorityRankException;
import dev.hogumeter.core.application.InvalidPurchaseCommandException;
import dev.hogumeter.core.application.InvalidRegistrationException;
import dev.hogumeter.core.application.ProductNotFoundException;
import dev.hogumeter.core.application.PurchaseNotFoundException;
import dev.hogumeter.core.application.WatchItemNotFoundException;
import dev.hogumeter.core.application.ComparisonAxisNotFoundException;
import dev.hogumeter.core.application.InvalidCoupangObservationException;
import dev.hogumeter.core.application.ListingNotFoundException;
import dev.hogumeter.core.application.ReviewItemNotFoundException;
import dev.hogumeter.core.application.UnclassifiedPromoteNotSupportedException;
import dev.hogumeter.core.application.UsedSearchNotFoundException;
import dev.hogumeter.core.domain.alert.InvalidAlertPolicyException;
import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.purchase.IllegalPurchaseTransitionException;
import dev.hogumeter.core.domain.watch.IllegalPinTransitionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 도메인 예외 → 에러코드 매핑. 봉투 없는 리소스 반환에 대응해 에러는 {@code {code, message}}(Q-2 잠정 확정). */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(VariantNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError variantNotFound(VariantNotFoundException e) {
		return new ApiError(VariantNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(InvalidBenchmarkPeriodException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidPeriod(InvalidBenchmarkPeriodException e) {
		return new ApiError(InvalidBenchmarkPeriodException.CODE, e.getMessage());
	}

	@ExceptionHandler(InvalidRegistrationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidRegistration(InvalidRegistrationException e) {
		return new ApiError(InvalidRegistrationException.CODE, e.getMessage());
	}

	@ExceptionHandler(InvalidAlertPolicyException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidAlertPolicy(InvalidAlertPolicyException e) {
		return new ApiError(InvalidAlertPolicyException.CODE, e.getMessage());
	}

	@ExceptionHandler(DemandAxisValueRequiredException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError demandAxisValueRequired(DemandAxisValueRequiredException e) {
		return new ApiError(DemandAxisValueRequiredException.CODE, e.getMessage());
	}

	@ExceptionHandler(ReviewItemNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError reviewItemNotFound(ReviewItemNotFoundException e) {
		return new ApiError(ReviewItemNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(UnclassifiedPromoteNotSupportedException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError unclassifiedPromote(UnclassifiedPromoteNotSupportedException e) {
		return new ApiError(UnclassifiedPromoteNotSupportedException.CODE, e.getMessage());
	}

	@ExceptionHandler(UsedSearchNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError usedSearchNotFound(UsedSearchNotFoundException e) {
		return new ApiError(UsedSearchNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(ListingNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError listingNotFound(ListingNotFoundException e) {
		return new ApiError(ListingNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(ComparisonAxisNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError comparisonAxisNotFound(ComparisonAxisNotFoundException e) {
		return new ApiError(ComparisonAxisNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(InvalidCoupangObservationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidCoupangObservation(InvalidCoupangObservationException e) {
		return new ApiError(InvalidCoupangObservationException.CODE, e.getMessage());
	}

	@ExceptionHandler(ExtensionAuthException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiError extensionAuthFailed(ExtensionAuthException e) {
		return new ApiError(ExtensionAuthException.CODE, e.getMessage());
	}

	@ExceptionHandler(RateLimitExceededException.class)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public ApiError rateLimitExceeded(RateLimitExceededException e) {
		return new ApiError(RateLimitExceededException.CODE, e.getMessage());
	}

	@ExceptionHandler(ProductNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError productNotFound(ProductNotFoundException e) {
		return new ApiError(ProductNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(DuplicatePriorityRankException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError duplicatePriorityRank(DuplicatePriorityRankException e) {
		return new ApiError(DuplicatePriorityRankException.CODE, e.getMessage());
	}

	@ExceptionHandler(DealEventNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError dealEventNotFound(DealEventNotFoundException e) {
		return new ApiError(DealEventNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(DealNotPinnableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError dealNotPinnable(DealNotPinnableException e) {
		return new ApiError(DealNotPinnableException.CODE, e.getMessage());
	}

	@ExceptionHandler(DealAlreadyPinnedException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError dealAlreadyPinned(DealAlreadyPinnedException e) {
		return new ApiError(DealAlreadyPinnedException.CODE, e.getMessage());
	}

	@ExceptionHandler(WatchItemNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError watchItemNotFound(WatchItemNotFoundException e) {
		return new ApiError(WatchItemNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(IllegalPinTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError illegalPinTransition(IllegalPinTransitionException e) {
		return new ApiError(IllegalPinTransitionException.CODE, e.getMessage());
	}

	@ExceptionHandler(PurchaseNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ApiError purchaseNotFound(PurchaseNotFoundException e) {
		return new ApiError(PurchaseNotFoundException.CODE, e.getMessage());
	}

	@ExceptionHandler(InvalidPurchaseCommandException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidPurchaseCommand(InvalidPurchaseCommandException e) {
		return new ApiError(InvalidPurchaseCommandException.CODE, e.getMessage());
	}

	@ExceptionHandler(IllegalPurchaseTransitionException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError illegalPurchaseTransition(IllegalPurchaseTransitionException e) {
		return new ApiError(IllegalPurchaseTransitionException.CODE, e.getMessage());
	}

	/**
	 * BE-15(코드리뷰 20260806) — 이 어드바이스가 안 다루는 예외(NullPointerException·
	 * DataIntegrityViolationException 등)는 Spring 기본 오류 응답으로 떨어져 {@code {code,message}}
	 * 계약이 케이스별로 깨진다. 근본 수정은 발생 지점에서 400/404로 막는 것이 우선이다(BE-11~14) —
	 * 이건 그 방어선을 벗어난 나머지를 위한 최종 안전망이다.
	 *
	 * <p>예외 메시지는 그대로 노출하지 않는다(SEC-01) — DataIntegrityViolationException은 SQL·제약
	 * 조건 이름을 담을 수 있다. HealthController와 같은 원칙: 타입 이름만 노출.
	 */
	@ExceptionHandler({ NullPointerException.class, DataIntegrityViolationException.class })
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ApiError unmapped(Exception e) {
		return new ApiError("INTERNAL_ERROR", e.getClass().getSimpleName());
	}

	public record ApiError(String code, String message) {
	}
}
