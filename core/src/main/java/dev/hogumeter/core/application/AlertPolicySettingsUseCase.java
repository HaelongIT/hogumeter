package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.alert.AlertPolicySettings;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REG-03 알림 정책 읽기·쓰기.
 *
 * <p><b>이 유스케이스가 생기기 전까지 {@code alert_policy}에는 프로덕션 writer가 없었다.</b>
 * {@link EvaluateAlertOnDealUseCase}는 이 테이블을 읽지만 행은 영원히 생기지 않았으므로,
 * 확정본 §107의 "OR [사용자 목표가 이하]" 트리거와 방해금지(AL-04)는 발화할 수 없었다.
 * 테스트만 손수 행을 넣고 GREEN이었다 — 부품별 GREEN은 계약을 보장하지 않는다.
 */
@Service
public class AlertPolicySettingsUseCase {

	private final VariantRepository variants;
	private final AlertPolicyRepository policies;
	private final EntityManager entityManager;

	public AlertPolicySettingsUseCase(VariantRepository variants, AlertPolicyRepository policies,
			EntityManager entityManager) {
		this.variants = variants;
		this.policies = policies;
		this.entityManager = entityManager;
	}

	/** @return 저장된 정책. 없으면 비어 있다 — 기본값을 지어내지 않는다(아래 갱신 주석 참조). */
	@Transactional(readOnly = true)
	public Optional<AlertPolicySettings> get(long variantId) {
		requireVariant(variantId);
		return policies.findByVariantId(variantId).map(AlertPolicySettingsUseCase::toSettings);
	}

	/**
	 * 있으면 갱신, 없으면 생성.
	 *
	 * <p>delete + insert가 아니라 <b>UPDATE</b>인 이유: {@code AlertPolicyEntity}는 {@code k_display}·
	 * {@code exclude_keywords}·{@code demand_axis_filter}를 매핑하지 않는다. 새로 insert하면 그 컬럼들이
	 * DB 기본값으로 조용히 되돌아간다 — 지금은 아무도 안 쓰니 아무도 모르고, 누군가 매핑을 붙이는 날
	 * 데이터가 사라진다. 벌크 UPDATE는 <b>우리가 아는 컬럼만</b> 건드린다. (엔티티에 setter가 없어
	 * 더티 체킹으로는 갱신할 수 없다 — 그 파일은 다른 개발자 소유다.)
	 */
	@Transactional
	public AlertPolicySettings update(long variantId, AlertPolicySettings settings) {
		requireVariant(variantId); // FK 위반(500) 대신 404로 답한다
		Optional<AlertPolicyEntity> existing = policies.findByVariantId(variantId);
		if (existing.isEmpty()) {
			policies.save(new AlertPolicyEntity(variantId, settings.targetPrice(), settings.periodMonths(),
					settings.quietHoursStart(), settings.quietHoursEnd()));
			return settings;
		}
		entityManager.createQuery("""
				update AlertPolicyEntity policy
				   set policy.targetPrice = :targetPrice,
				       policy.periodMonths = :periodMonths,
				       policy.quietHoursStart = :quietHoursStart,
				       policy.quietHoursEnd = :quietHoursEnd
				 where policy.variantId = :variantId
				""")
			.setParameter("targetPrice", settings.targetPrice())
			.setParameter("periodMonths", settings.periodMonths())
			.setParameter("quietHoursStart", settings.quietHoursStart())
			.setParameter("quietHoursEnd", settings.quietHoursEnd())
			.setParameter("variantId", variantId)
			.executeUpdate();
		// 벌크 UPDATE는 영속성 컨텍스트를 우회한다 — 방금 고친 행을 다시 읽으면 캐시된 옛 값이 나온다.
		// 컨텍스트 전체를 clear()하면 같은 트랜잭션의 남의 엔티티까지 날아간다. 이 행만 다시 읽는다.
		entityManager.refresh(existing.get());
		return settings;
	}

	private void requireVariant(long variantId) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
	}

	private static AlertPolicySettings toSettings(AlertPolicyEntity entity) {
		return new AlertPolicySettings(entity.getTargetPrice(), entity.getPeriodMonths(),
				entity.getQuietHoursStart(), entity.getQuietHoursEnd());
	}
}
