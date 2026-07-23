package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.SitePollStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 관측시계(docs/03 3-2) — 신선도의 기준 시각을 벽시계가 아니라 <b>마지막 성공 폴링</b>에서 읽는다.
 * 수집이 멈추면 이 값이 멈추고, 그래서 staleness도 멈춘다("무지를 부재로 오독" 방지).
 *
 * <p>폴링 기록이 하나도 없으면 시계가 <b>없는</b> 것이다 — 벽시계로 대신하되 그 사실을 값과 함께
 * 실어 보낸다({@code measured=false}). 조용히 now로 떨어지면 "수집이 멈춘 적 없다"는 거짓말이 된다.
 */
@Service
public class ObservationClock {

	private final SitePollStateRepository states;
	private final Clock clock;

	public ObservationClock(SitePollStateRepository states, Clock clock) {
		this.states = states;
		this.clock = clock;
	}

	public Reading read() {
		Optional<Instant> earliest = states.earliestSuccessfulPoll();
		return earliest.map(at -> new Reading(at, true))
				.orElseGet(() -> new Reading(clock.instant(), false));
	}

	/**
	 * @param at 신선도 기산점
	 * @param measured 실제 폴링 기록에서 읽었는가(false = 벽시계 대체)
	 */
	public record Reading(Instant at, boolean measured) {
	}
}
