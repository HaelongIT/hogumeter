package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * DIGEST 전체 조립(docs/18) — 등록된 모든 variant의 행 + ⑤ 큐(전역 스톡) + ⑥ 조용한 주 판정을
 * 한 번에 모은다. {@link AssembleVariantDigestUseCase}(variant 1건)의 배치 판이다.
 *
 * <p>렌더링(문구·정렬·플로우/스톡 분류)은 아직 없다 — 이 유스케이스는 <b>재료를 다 모으는 것</b>까지만
 * 한다(docs/91 Q-81). 발송·저장물 갱신도 여기 없다.
 */
@Service
public class AssembleDigestUseCase {

	private final VariantRepository variants;
	private final AssembleVariantDigestUseCase perVariant;
	private final ComputeDigestQuietWeekUseCase quietWeek;
	private final GetReviewQueueUseCase reviewQueue;

	public AssembleDigestUseCase(VariantRepository variants, AssembleVariantDigestUseCase perVariant,
			ComputeDigestQuietWeekUseCase quietWeek, GetReviewQueueUseCase reviewQueue) {
		this.variants = variants;
		this.perVariant = perVariant;
		this.quietWeek = quietWeek;
		this.reviewQueue = reviewQueue;
	}

	public Digest assemble() {
		List<Long> variantIds = variants.findAll().stream().map(VariantEntity::getId).toList();
		List<VariantDigestRow> rows = variantIds.stream().map(perVariant::assemble).toList();
		return new Digest(rows, reviewQueue.pending(), quietWeek.isQuietWeek(variantIds));
	}

	/**
	 * @param variantRows 등록된 모든 variant의 다이제스트 행(① 최고 기회·② 전환·③ 관찰 경과)
	 * @param queue ⑤ 전역 스톡(미상 큐)
	 * @param isQuietWeek ⑥ 조용한 주 — 전 variant에 플로우·전환이 없고(공백은 지금 고정 false)
	 */
	public record Digest(List<VariantDigestRow> variantRows, List<PendingItem> queue, boolean isQuietWeek) {
	}
}
