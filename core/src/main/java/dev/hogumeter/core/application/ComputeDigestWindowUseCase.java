package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.digest.DigestWindow;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * DIG-03 창 배선(docs/18) — {@link DigestWindow}(순수)가 이미 있었지만 <b>호출자가 0</b>이었다
 * (테스트만 불렀다). 이 유스케이스가 그 첫 소비자다.
 *
 * <p><b>활성 시각</b> = variant가 속한 product의 등록 시각({@code product.created_at}) — variant
 * 자신은 생성 시각을 안 갖는다(V1 스키마). "이 variant를 언제부터 추적했는가"의 가장 가까운 실측
 * 대리값이다.
 *
 * <p><b>직전 성공 발송</b> = {@code digest_state} 행의 {@code last_sent_at}. 행이 없으면(한 번도
 * 발송 안 함) 활성 시각을 그 자리에 대신 넣는다 — {@link DigestWindow#of}는 두 값 중 늦은 쪽을
 * 창 시작으로 삼으므로, 활성 시각을 두 자리에 다 넣으면 정확히 "첫 창 = 활성 시각부터"가 된다.
 *
 * <p>이 유스케이스는 창 <b>계산</b>까지만 한다 — 섹션 조립·발송·{@code digest_state} 갱신은
 * 아직 없다(docs/91 Q-81, 후속 증분).
 */
@Service
public class ComputeDigestWindowUseCase {

	private final VariantRepository variants;
	private final ProductRepository products;
	private final DigestStateRepository digestStates;
	private final Clock clock;

	public ComputeDigestWindowUseCase(VariantRepository variants, ProductRepository products,
			DigestStateRepository digestStates, Clock clock) {
		this.variants = variants;
		this.products = products;
		this.digestStates = digestStates;
		this.clock = clock;
	}

	public DigestWindow window(long variantId) {
		VariantEntity variant = variants.findById(variantId)
				.orElseThrow(() -> new VariantNotFoundException(variantId));
		// FK 제약상 product는 항상 존재한다 — 없으면 데이터 정합성이 깨진 것이라 같은 예외로 드러낸다.
		ProductEntity product = products.findById(variant.getProductId())
				.orElseThrow(() -> new VariantNotFoundException(variantId));
		Instant activationTime = product.getCreatedAt();
		Instant priorSuccessfulSend = digestStates.findById(variantId)
				.map(DigestStateEntity::getLastSentAt)
				.orElse(activationTime);
		return DigestWindow.of(priorSuccessfulSend, activationTime, clock.instant());
	}
}
