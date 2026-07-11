package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.used.BonusMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V3 used_search_bonus_group 테이블(USED-01). 그룹=행 + 키워드 text[] 배열(그룹 내 OR). mode SORT|TRIGGER. */
@Entity
@Table(name = "used_search_bonus_group")
public class UsedSearchBonusGroupEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "used_search_id", nullable = false)
	private Long usedSearchId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private BonusMode mode;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> keywords;

	protected UsedSearchBonusGroupEntity() {
	}

	public UsedSearchBonusGroupEntity(Long usedSearchId, BonusMode mode, List<String> keywords) {
		this.usedSearchId = usedSearchId;
		this.mode = mode;
		this.keywords = keywords;
	}

	public Long getId() {
		return id;
	}

	public Long getUsedSearchId() {
		return usedSearchId;
	}

	public BonusMode getMode() {
		return mode;
	}

	public List<String> getKeywords() {
		return keywords;
	}
}
