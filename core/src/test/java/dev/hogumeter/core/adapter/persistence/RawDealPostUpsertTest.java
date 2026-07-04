package dev.hogumeter.core.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** BM-01 AC-1(재수집 멱등)·AC-2(상태 변화 감지) — 실 스키마(Flyway V1) + Testcontainers. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RawDealPostUpsertTest {

	private static final Instant CAPTURED = Instant.parse("2026-07-04T00:00:00Z");

	@Autowired
	RawDealPostRepository repository;

	private RawDealPostUpserter upserter;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		upserter = new RawDealPostUpserter(repository);
	}

	@Test
	void reCollectionOfSamePostIsIdempotent() {
		RawDealPost first = upserter.upsert("ppomppu", "123", "https://p.test/123", "아이폰 17", CAPTURED, "ACTIVE");
		RawDealPost second = upserter.upsert("ppomppu", "123", "https://p.test/123", "아이폰 17", CAPTURED, "ACTIVE");

		assertThat(repository.count()).isEqualTo(1); // 중복 행 없음 (UNIQUE(site, post_id))
		assertThat(second.getId()).isEqualTo(first.getId());
	}

	@Test
	void statusChangeIsDetectedOnReCollection() {
		upserter.upsert("ppomppu", "123", "https://p.test/123", "아이폰 17", CAPTURED, "ACTIVE");
		RawDealPost updated = upserter.upsert("ppomppu", "123", "https://p.test/123", "아이폰 17 [품절]", CAPTURED,
				"SOLD_OUT");

		assertThat(repository.count()).isEqualTo(1);
		assertThat(updated.getStatus()).isEqualTo("SOLD_OUT");
		assertThat(repository.findBySiteAndPostId("ppomppu", "123").orElseThrow().getStatus()).isEqualTo("SOLD_OUT");
	}

	@Test
	void differentSitesWithSamePostIdCoexist() {
		upserter.upsert("ppomppu", "123", "https://p.test/123", "제목", CAPTURED, "ACTIVE");
		upserter.upsert("ruliweb", "123", "https://r.test/123", "제목", CAPTURED, "ACTIVE");

		assertThat(repository.count()).isEqualTo(2); // 자연키는 (site, post_id) 복합
	}
}
