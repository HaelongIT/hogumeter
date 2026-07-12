package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.alert.FollowUpEvaluator;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AL-03 후속 알림 발송(Q-67). 전이(가격변화·종료 등)한 딜 중 <b>첫 알림이 나갔던 딜에만</b>
 * ({@link FollowUpEvaluator}) 후속을 보낸다 — 처음부터 알림 대상이 아니었던 딜은 전이해도 조용하다.
 * {@code (deal_event_id, kind)} 이력으로 종류별 1회만 발송한다(매 틱 도는 후속이 재발송하지 않게).
 *
 * <p>특히 {@code ENDED}는 "지금 사라"를 받고 달려간 사람에게 "끝났다"를 말할 유일한 경로다. 발송은
 * out-port 스텁이다 — 텔레그램 실전송은 봇 토큰(Q-20) 뒤라, 지금은 배선만 완결하고 로그로 흐른다.
 */
@Service
public class FollowUpAlertUseCase {

	private final DealAlertRepository alerts;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final AlertSender sender;
	private final FollowUpEvaluator evaluator = new FollowUpEvaluator();

	public FollowUpAlertUseCase(DealAlertRepository alerts, DealEventRepository dealEvents,
			DealEventMapper mapper, AlertSender sender) {
		this.alerts = alerts;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.sender = sender;
	}

	/**
	 * 전이한 딜들에 후속 알림을 보낸다.
	 *
	 * @return 실제로 발송한 수(첫 알림 없음·이미 발송·딜 없음은 건너뛴다).
	 */
	@Transactional
	public int sendFollowUps(List<Long> dealEventIds, FollowUpKind kind) {
		int sent = 0;
		for (Long id : dealEventIds) {
			boolean alreadyAlerted = alerts.existsByDealEventIdAndKind(id, DealAlertEntity.FIRST);
			if (!evaluator.shouldSendFollowUp(kind, alreadyAlerted)) {
				continue; // 첫 알림이 안 나간 딜엔 후속도 없다
			}
			if (alerts.existsByDealEventIdAndKind(id, kind.name())) {
				continue; // 이 종류 후속은 이미 보냈다(멱등)
			}
			DealEventEntity entity = dealEvents.findById(id).orElse(null);
			if (entity == null) {
				continue; // 사라진 딜 — 근거 없이 알리지 않는다
			}
			sender.send(new AlertMessage(mapper.toDomain(entity), null, null, kind));
			alerts.save(new DealAlertEntity(id, kind.name()));
			sent++;
		}
		return sent;
	}
}
