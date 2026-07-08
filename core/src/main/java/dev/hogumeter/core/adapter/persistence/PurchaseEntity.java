package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.Snapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** V2 purchase 테이블 JPA 엔티티 — Purchase(PUR-01) + Snapshot(PUR-02)를 한 행으로 보관. */
@Entity
@Table(name = "purchase")
public class PurchaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	@Column(name = "demand_axis_value")
	private String demandAxisValue;

	@Column(name = "paid_price", nullable = false)
	private long paidPrice;

	@Column(name = "purchased_at", nullable = false)
	private Instant purchasedAt;

	@Column(name = "observation_days", nullable = false)
	private int observationDays;

	@Column(name = "linked_deal_event_id")
	private Long linkedDealEventId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PurchaseState state;

	// PUR-02 스냅샷 (as-of 동결)
	@Column(name = "snap_benchmark_price")
	private Long snapBenchmarkPrice;

	@Column(name = "snap_tier")
	@Enumerated(EnumType.STRING)
	private Tier snapTier;

	@Column(name = "snap_n", nullable = false)
	private int snapN;

	@Column(name = "snap_m", nullable = false)
	private int snapM;

	@Column(name = "snap_sparse_lowest")
	private Long snapSparseLowest;

	@Column(name = "snap_paid_gap")
	private Long snapPaidGap;

	@Column(name = "snap_basis")
	private String snapBasis;

	@Column(name = "snap_unobserved", nullable = false)
	private boolean snapUnobserved;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PurchaseEntity() {
	}

	public PurchaseEntity(Purchase purchase, Snapshot snapshot) {
		this.variantId = purchase.variantId();
		this.demandAxisValue = purchase.demandAxisValue();
		this.paidPrice = purchase.paidPrice();
		this.purchasedAt = purchase.purchasedAt();
		this.observationDays = purchase.observationDays();
		this.linkedDealEventId = purchase.linkedDealEventId();
		this.state = purchase.state();
		this.snapBenchmarkPrice = snapshot.benchmarkPrice();
		this.snapTier = snapshot.tier();
		this.snapN = snapshot.n();
		this.snapM = snapshot.m();
		this.snapSparseLowest = snapshot.sparseLowest();
		this.snapPaidGap = snapshot.paidGap();
		this.snapBasis = snapshot.basis();
		this.snapUnobserved = snapshot.unobserved();
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	/** 저장 행 → 도메인 Purchase 복원. */
	public Purchase toDomain() {
		return new Purchase(variantId, demandAxisValue, paidPrice, purchasedAt, observationDays,
				linkedDealEventId, state);
	}

	/** 저장 행 → 동결 스냅샷 복원. */
	public Snapshot toSnapshot() {
		return new Snapshot(snapBenchmarkPrice, snapTier, snapN, snapM, snapSparseLowest, snapPaidGap,
				snapBasis, snapUnobserved);
	}

	public Long getId() {
		return id;
	}

	public Long getVariantId() {
		return variantId;
	}

	public PurchaseState getState() {
		return state;
	}

	public Long getSnapBenchmarkPrice() {
		return snapBenchmarkPrice;
	}

	public Long getSnapPaidGap() {
		return snapPaidGap;
	}

	public boolean isSnapUnobserved() {
		return snapUnobserved;
	}
}
