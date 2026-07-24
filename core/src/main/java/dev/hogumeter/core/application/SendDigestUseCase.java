package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.AssembleDigestUseCase.Digest;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.application.port.out.DigestSender;
import dev.hogumeter.core.domain.digest.DigestSplitter;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DIGEST 발송(docs/18) — 렌더링된 본문을 {@link DigestSplitter}로 나눠(DIG-02 절단 금지·연번)
 * {@link DigestSender}로 순서대로 보낸다. <b>전 분할 성공 시에만</b> variant마다
 * {@link RecordDigestSentUseCase}로 저장물을 갱신한다(REL-03 원자성 — 절반만 나간 발송을 "보냈다"고
 * 기록하지 않는다).
 *
 * <p>색·문맥·basis 3성분의 출처:
 * <ul>
 * <li><b>색</b> = {@code row.transition().to()} — 조립 단계가 이미 구한 현재 색을 그대로 쓴다(재계산 없음).</li>
 * <li><b>basis 모드</b> = product의 {@code DemandAxisMode}.</li>
 * <li><b>문맥</b> = 그 variant의 Purchase 중 <b>가장 최근({@code purchasedAt})</b> 것의
 * {@code ObservationContext.mode}. <b>한계(docs/91 Q-81)</b>: variant당 Purchase가 여럿 공존할 수
 * 있는데(PUR-01 "독립 관찰") 이 값은 색 비교에 안 쓰이는 억제 신호 부가정보라(DigestRules는 색만 본다)
 * "가장 최근 구매 하나"로 근사했다 — 다중 구매의 대표값 규약은 아직 확정되지 않았다. 구매가 없으면 null.
 * </ul>
 *
 * <p><b>여전히 없는 것</b>: quiet hours 게이트(다이제스트는 전역 개념인데 전역 quiet hours 설정이
 * 아직 없다 — 스케줄이 일요일 20시 KST 고정이라 당장의 위험은 낮다).
 */
@Service
public class SendDigestUseCase {

	/** 텔레그램 sendMessage 본문 상한(공개 문서상 4096). */
	static final int TELEGRAM_MAX_LENGTH = 4096;

	private final AssembleDigestUseCase assembler;
	private final RenderDigestUseCase render;
	private final DigestSender sender;
	private final RecordDigestSentUseCase recordSent;
	private final VariantRepository variants;
	private final ProductRepository products;
	private final GetPurchaseObservationsUseCase observations;
	private final int maxLength;

	@Autowired
	public SendDigestUseCase(AssembleDigestUseCase assembler, RenderDigestUseCase render, DigestSender sender,
			RecordDigestSentUseCase recordSent, VariantRepository variants, ProductRepository products,
			GetPurchaseObservationsUseCase observations) {
		this(assembler, render, sender, recordSent, variants, products, observations, TELEGRAM_MAX_LENGTH);
	}

	/** 테스트 seam — 작은 한도로 분할·연번을 실제로 유발해 검증한다. */
	SendDigestUseCase(AssembleDigestUseCase assembler, RenderDigestUseCase render, DigestSender sender,
			RecordDigestSentUseCase recordSent, VariantRepository variants, ProductRepository products,
			GetPurchaseObservationsUseCase observations, int maxLength) {
		this.assembler = assembler;
		this.render = render;
		this.sender = sender;
		this.recordSent = recordSent;
		this.variants = variants;
		this.products = products;
		this.observations = observations;
		this.maxLength = maxLength;
	}

	public DigestSendReport send() {
		Digest digest = assembler.assemble();
		List<String> parts = DigestSplitter.split(render.render(), maxLength);
		int sent = 0;
		for (String part : parts) {
			if (sender.sendDigest(part)) {
				sent++;
			}
		}
		boolean allSucceeded = sent == parts.size();
		if (allSucceeded) {
			for (VariantDigestRow row : digest.variantRows()) {
				recordSent.recordSent(row.variantId(), row.transition().to().name(), contextFor(row.variantId()),
						basisModeFor(row.variantId()));
			}
		}
		return new DigestSendReport(parts.size(), sent, allSucceeded);
	}

	/** 가장 최근 Purchase의 관찰 문맥 모드. 구매가 없으면 null(클래스 javadoc의 근사). */
	private String contextFor(long variantId) {
		return observations.forVariant(variantId).stream()
				.max(Comparator.comparing(PurchaseObservation::purchasedAt))
				.map(o -> o.context().mode().name())
				.orElse(null);
	}

	private String basisModeFor(long variantId) {
		return variants.findById(variantId)
				.map(VariantEntity::getProductId)
				.flatMap(products::findById)
				.map(ProductEntity::getDemandAxisMode)
				.map(Enum::name)
				.orElse(null);
	}

	/**
	 * @param parts 분할된 조각 수(안 나뉘면 1)
	 * @param sent 실제로 나간 조각 수
	 * @param allSucceeded 전 분할 성공 — 이때만 저장물을 갱신한다(클래스 javadoc)
	 */
	public record DigestSendReport(int parts, int sent, boolean allSucceeded) {
	}
}
