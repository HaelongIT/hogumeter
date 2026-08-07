package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.product.DemandAxisMode;
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

/** V1 product 테이블 JPA 엔티티(등록). */
@Entity
@Table(name = "product")
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String category;

	@Column(name = "demand_axis_mode", nullable = false)
	@Enumerated(EnumType.STRING)
	private DemandAxisMode demandAxisMode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** PRI ②축소(docs/19) — 유일 순번. null = 미지정(부분 유니크 인덱스라 서로 충돌하지 않는다). */
	@Column(name = "priority_rank")
	private Integer priorityRank;

	/** PRI ②축소 — 중고·장외 구매 이탈용 수동 완료(취소 가능, 알림은 유지). */
	@Column(name = "manually_completed", nullable = false)
	private boolean manuallyCompleted;

	/** Q-91(docs/91) — 수동 보관(취소 가능). 표시 손잡이만: 목록에서 숨기되 매칭·폴링은 안 건드린다. */
	@Column(nullable = false)
	private boolean archived;

	protected ProductEntity() {
	}

	public ProductEntity(String name, String category, DemandAxisMode demandAxisMode) {
		this.name = name;
		this.category = category;
		this.demandAxisMode = demandAxisMode;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public DemandAxisMode getDemandAxisMode() {
		return demandAxisMode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Integer getPriorityRank() {
		return priorityRank;
	}

	public void setPriorityRank(Integer priorityRank) {
		this.priorityRank = priorityRank;
	}

	public boolean isManuallyCompleted() {
		return manuallyCompleted;
	}

	public void setManuallyCompleted(boolean manuallyCompleted) {
		this.manuallyCompleted = manuallyCompleted;
	}

	public boolean isArchived() {
		return archived;
	}

	public void setArchived(boolean archived) {
		this.archived = archived;
	}
}
