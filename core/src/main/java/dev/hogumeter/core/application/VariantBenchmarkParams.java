package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.alert.AlertPolicySettings;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * variant별 기준가 파라미터(Q-48 ①). 수치는 대부분 시스템 고정이지만 <b>K_display만 사용자 손잡이</b>라
 * (확정본 §217, 원칙 4 — 표시를 바꾸는 설정은 사용자에게) 정책에서 읽어 갈아끼운다.
 *
 * <p><b>해석은 여기 한 곳에만 둔다.</b> 정책을 이미 읽은 호출자(알림 판정)와 안 읽은 호출자(조회)가 각자
 * "미설정이면 몇 K인가"를 해석하면 한쪽이 조용히 다른 값을 쓴다 — 그래서 {@link #from(Optional)}이 정본이고
 * {@link #of(long)}은 읽어서 그걸 부를 뿐이다.
 *
 * <p>이 자리가 "variant별 정책 → 도메인 파라미터" 이음새다. 다음 손잡이(제외키워드 Q-28 등)도 여기로 온다.
 */
@Service
public class VariantBenchmarkParams {

	private final AlertPolicyRepository policies;

	public VariantBenchmarkParams(AlertPolicyRepository policies) {
		this.policies = policies;
	}

	/** 정책을 읽어 이 variant의 파라미터를 만든다. 미설정이면 기본 K. */
	public BenchmarkParams of(long variantId) {
		return from(policies.findByVariantId(variantId));
	}

	/** 이미 정책을 읽은 호출자용(중복 조회 회피). 미설정이면 확정본 기본 K — 정본은 한 상수다. */
	public static BenchmarkParams from(Optional<AlertPolicyEntity> policy) {
		int kDisplay = policy.map(AlertPolicyEntity::getKDisplay).orElse(AlertPolicySettings.DEFAULT_K_DISPLAY);
		return BenchmarkParams.defaults().withKDisplay(kDisplay);
	}
}
