package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.HeldAlertEntity;
import dev.hogumeter.core.adapter.persistence.HeldAlertRepository;
import dev.hogumeter.core.domain.alert.QuietHours;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AL-04/07(Q-20 ②): 방해금지로 보류된 알림을 <b>방해금지가 끝난 뒤 재평가해</b> 발송한다. 매 틱 돌며,
 * 각 보류 딜의 variant 방해금지가 아직이면 그대로 두고, 끝났으면 {@link EvaluateAlertOnDealUseCase#evaluate}를
 * <b>다시 부른다</b> — 이제 방해금지가 아니라 게이트가 SEND_NOW를 내므로 실제로 나간다.
 *
 * <p><b>왜 저장된 본문이 아니라 재평가인가</b>(AL-07 "발송 시점 재평가"): 밤새 상황이 바뀐다. 재평가는 현재
 * 기준가·현재가·상태로 다시 판정하므로 — 여전히 좋은 딜이면 <b>최신값으로</b> 보내고, 더는 자격이 없으면
 * (기준가가 내려갔거나 딜이 끝났으면) <b>드롭</b>한다. 지어낸 밤사이 값으로 알리지 않는다.
 *
 * <p>처리한 보류 건은 발송·드롭 무관하게 큐에서 지운다. {@code @Transactional}이라 재평가의 부수효과
 * (deal_alert FIRST 기록)와 삭제가 원자적이다 — 커밋 전 실패하면 둘 다 없던 일이 돼 재시도가 이중 발송하지 않는다.
 *
 * <p><b>한계(v1)</b>: 재평가에서 딜이 종료(ENDED)됐으면 억제돼 드롭된다 — AL-04의 "종료 시 (종료됨) 표기 발송"은
 * 후속(Q-20 ①·후속 알림)이 채운다. 보류 중 끝난 딜을 굳이 "끝났다"고 알리는 실익이 낮아 v1은 드롭한다(docs/91 Q-20 ②).
 */
@Service
public class FlushHeldAlertsUseCase {

	private final HeldAlertRepository held;
	private final AlertPolicyRepository policies;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final EvaluateAlertOnDealUseCase evaluate;
	private final Clock clock;

	public FlushHeldAlertsUseCase(HeldAlertRepository held, AlertPolicyRepository policies,
			DealEventRepository dealEvents, DealEventMapper mapper, EvaluateAlertOnDealUseCase evaluate, Clock clock) {
		this.held = held;
		this.policies = policies;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.evaluate = evaluate;
		this.clock = clock;
	}

	@Transactional
	public FlushReport flush() {
		int sent = 0;
		int dropped = 0;
		for (HeldAlertEntity entry : held.findAll()) {
			if (stillQuiet(entry.getVariantId())) {
				continue; // 아직 방해금지 — 다음 틱에 다시 본다
			}
			DealEventEntity deal = dealEvents.findById(entry.getDealEventId()).orElse(null);
			if (deal != null
					&& evaluate.evaluate(entry.getVariantId(), deal.getId(), mapper.toDomain(deal)) == DispatchOutcome.SENT) {
				sent++;
			}
			else {
				dropped++; // 딜이 사라졌거나 더는 자격 없음(NO_ALERT) — 지어낸 밤사이 값으로 알리지 않는다
			}
			held.delete(entry); // 발송·드롭 무관하게 처리 완료 — 다시 보류되면 evaluate가 새로 넣는다
		}
		return new FlushReport(sent, dropped);
	}

	/** 이 variant가 지금 방해금지 시간인가. 정책이 없거나 방해금지 미설정이면 아니다(=플러시 대상). */
	private boolean stillQuiet(long variantId) {
		return policies.findByVariantId(variantId)
				.map(p -> QuietHours.isQuiet(hourNow(), p.getQuietHoursStart(), p.getQuietHoursEnd()))
				.orElse(false);
	}

	private int hourNow() {
		return ZonedDateTime.ofInstant(clock.instant(), clock.getZone()).getHour();
	}

	/** @param flushed 방해금지가 끝나 실제로 발송된 수. @param dropped 재평가에서 자격을 잃어 드롭된 수. */
	public record FlushReport(int flushed, int dropped) {

		public static FlushReport empty() {
			return new FlushReport(0, 0);
		}
	}
}
