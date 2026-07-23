package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.DemandAxisValueRequiredException;
import dev.hogumeter.core.application.InvalidRegistrationException;
import dev.hogumeter.core.application.ComparisonAxisNotFoundException;
import dev.hogumeter.core.application.ListingNotFoundException;
import dev.hogumeter.core.application.ReviewItemNotFoundException;
import dev.hogumeter.core.application.UnclassifiedPromoteNotSupportedException;
import dev.hogumeter.core.application.UsedSearchNotFoundException;
import dev.hogumeter.core.domain.alert.InvalidAlertPolicyException;
import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
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

	public record ApiError(String code, String message) {
	}
}
