package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.purchase.ReportCard;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** V10 report_card 테이블 JPA 엔티티 — PUR-04 성적표(ReportCard) 한 장을 한 행으로 보관. 재발급 없음(purchase_id 유니크). */
@Entity
@Table(name = "report_card")
public class ReportCardEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "purchase_id", nullable = false)
	private Long purchaseId;

	@Column(nullable = false)
	private boolean unobserved;

	@Column(nullable = false)
	private int n;

	@Column(name = "cheaper_count", nullable = false)
	private int cheaperCount;

	@Column
	private BigDecimal percentile;

	@Column(name = "lowest_opportunity")
	private Long lowestOpportunity;

	@Column(name = "paid_price", nullable = false)
	private long paidPrice;

	@Column(name = "paid_gap")
	private Long paidGap;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	protected ReportCardEntity() {
	}

	public ReportCardEntity(long purchaseId, ReportCard card, Instant issuedAt) {
		this.purchaseId = purchaseId;
		this.unobserved = card.unobserved();
		this.n = card.n();
		this.cheaperCount = card.cheaperCount();
		this.percentile = card.percentile();
		this.lowestOpportunity = card.lowestOpportunity();
		this.paidPrice = card.paidPrice();
		this.paidGap = card.paidGap();
		this.issuedAt = issuedAt;
	}

	/** 저장 행 → 도메인 ReportCard 복원. */
	public ReportCard toDomain() {
		return new ReportCard(unobserved, n, cheaperCount, percentile, lowestOpportunity, paidPrice, paidGap);
	}

	public Long getId() {
		return id;
	}

	public Long getPurchaseId() {
		return purchaseId;
	}
}
