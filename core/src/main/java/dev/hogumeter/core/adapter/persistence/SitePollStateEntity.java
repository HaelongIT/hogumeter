package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V12 site_poll_state — 사이트별 마지막 성공 폴링 시각. <b>생산자는 collector</b>(Python)이고 core는
 * 신선도 계산을 위해 읽는다. 실패 상태(연속 실패·stopped)는 담지 않는다 — Q-59/D-3 참조.
 * {@code updated_at}은 DB default라 매핑하지 않는다(쓰는 쪽이 collector이므로 core가 정할 값이 아니다).
 */
@Entity
@Table(name = "site_poll_state")
public class SitePollStateEntity {

	@Id
	private String site;

	@Column(name = "last_successful_poll_at", nullable = false)
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
