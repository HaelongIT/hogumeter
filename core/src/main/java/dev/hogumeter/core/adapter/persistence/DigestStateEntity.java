package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V16 digest_state — variant당 다이제스트(docs/18) 마지막 발송 상태. 행 부재 = 한 번도 발송 안 함.
 * {@code updatedAt}은 DB default라 매핑하지 않는다(쓰는 쪽이 발송 유스케이스지만 갱신 시각 자체를
 * core가 읽을 일이 아직 없다 — 필요해지면 그때 매핑한다).
 */
@Entity
@Table(name = "digest_state")
public class DigestStateEntity {

	@Id
	@Column(name = "variant_id")
	private Long variantId;

	@Column(name = "last_sent_at")
	private Instant lastSentAt;

	@Column(name = "stored_color")
	private String storedColor;

	@Column(name = "stored_context")
	private String storedContext;

	@Column(name = "stored_basis_mode")
	private String storedBasisMode;

	protected DigestStateEntity() {
	}

	public DigestStateEntity(Long variantId, Instant lastSentAt, String storedColor, String storedContext,
			String storedBasisMode) {
		this.variantId = variantId;
		this.lastSentAt = lastSentAt;
		this.storedColor = storedColor;
		this.storedContext = storedContext;
		this.storedBasisMode = storedBasisMode;
	}

	public Long getVariantId() {
		return variantId;
	}

	public Instant getLastSentAt() {
		return lastSentAt;
	}

	public String getStoredColor() {
		return storedColor;
	}

	public String getStoredContext() {
		return storedContext;
	}

	public String getStoredBasisMode() {
		return storedBasisMode;
	}
}
