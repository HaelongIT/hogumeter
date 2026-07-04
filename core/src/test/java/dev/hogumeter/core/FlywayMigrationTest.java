package dev.hogumeter.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M0-3: Flyway V1이 신품 코어 루프(M1) 스키마를 생성하는지 검증한다.
 * used(중고) 테이블은 M2에서 V2로 추가한다 — docs/91 Q-4.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationTest {

	private static final List<String> CORE_LOOP_TABLES = List.of(
			"product", "product_axis", "variant", "alias_dictionary",
			"raw_deal_post", "deal_event", "deal_event_source",
			"price_history", "alert_policy", "review_queue_item", "global_setting");

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void v1MigrationCreatesCoreLoopTables() {
		List<String> tables = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'",
				String.class);
		assertThat(tables).containsAll(CORE_LOOP_TABLES);
	}

	@Test
	void rawDealPostEnforcesNaturalKey() {
		// 멱등 수집의 근간 (REL-01): UNIQUE(site, post_id)
		List<String> uniques = jdbc.queryForList(
				"""
				select c.conname from pg_constraint c
				join pg_class t on t.oid = c.conrelid
				where t.relname = 'raw_deal_post' and c.contype = 'u'
				""",
				String.class);
		assertThat(uniques).isNotEmpty();
	}
}
