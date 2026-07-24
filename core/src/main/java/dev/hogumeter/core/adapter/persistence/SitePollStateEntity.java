package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V12 site_poll_state — 사이트별 마지막 성공 폴링 시각. <b>생산자는 collector</b>(Python)이고 core는
 * 신선도 계산을 위해 읽는다. V15(Q-59/D-3)가 연속 실패·{@code next_attempt_at}·{@code stopped}
 * 컬럼을 더했지만 core는 그 값을 쓰지 않으므로(재개는 운영자가 DB를 직접 UPDATE) <b>여기 매핑하지
 * 않는다</b> — core가 안 쓰는 컬럼을 매핑하면 다음에 그 컬럼을 실제로 쓰려는 코드가 이 엔티티부터
 * 고쳐야 하는 줄 알고 혼동한다. {@code last_successful_poll_at}은 V15부터 nullable이다 — 한 번도
 * 성공한 적 없는 사이트의 커서도 이제 존재할 수 있다(SQL {@code min()}이 NULL을 걸러내므로
 * {@link dev.hogumeter.core.application.ObservationClock}은 영향받지 않는다).
 * {@code updated_at}은 DB default라 매핑하지 않는다(쓰는 쪽이 collector이므로 core가 정할 값이 아니다).
 */
@Entity
@Table(name = "site_poll_state")
public class SitePollStateEntity {

	@Id
	private String site;

	@Column(name = "last_successful_poll_at")
	private Instant lastSuccessfulPollAt;

	protected SitePollStateEntity() {
	}

	public SitePollStateEntity(String site, Instant lastSuccessfulPollAt) {
		this.site = site;
		this.lastSuccessfulPollAt = lastSuccessfulPollAt;
	}

	public String getSite() {
		return site;
	}

	public Instant getLastSuccessfulPollAt() {
		return lastSuccessfulPollAt;
	}
}
