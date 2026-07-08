package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * raw_deal_post 계약 테이블(collector↔core)의 JPA 엔티티. UNIQUE(site, post_id) = 멱등 수집(REL-01).
 * nullable 컬럼(body_text·headline_price·posted_at·reaction_score·raw jsonb)은 이 슬라이스에서 미매핑
 * (ddl-auto=validate는 매핑 컬럼만 검증). Flyway가 스키마 소유.
 */
@Entity
@Table(name = "raw_deal_post")
public class RawDealPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String site;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(nullable = false)
	private String url;

	@Column(nullable = false)
	private String title;

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	@Column(nullable = false)
	private String status;

	protected RawDealPost() {
	}

	public RawDealPost(String site, String postId, String url, String title, Instant capturedAt, String status) {
		this.site = site;
		this.postId = postId;
		this.url = url;
		this.title = title;
		this.capturedAt = capturedAt;
		this.status = status;
	}

	public Long getId() {
		return id;
	}

	public String getSite() {
		return site;
	}

	public String getPostId() {
		return postId;
	}

	public String getUrl() {
		return url;
	}

	public String getTitle() {
		return title;
	}

	public String getStatus() {
		return status;
	}

	/** 재수집 시 변화 가능한 필드 갱신(상태 변화 감지 등, BM-01 AC-2). */
	public void refreshFrom(String url, String title, Instant capturedAt, String status) {
		this.url = url;
		this.title = title;
		this.capturedAt = capturedAt;
		this.status = status;
	}
}
