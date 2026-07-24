package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** DIG-04 ⑤ "N회째 미확인"의 쓰는 쪽 — 지목한 id만 +1, 나머지는 안 건드린다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IncrementDigestQueueAppearancesUseCaseTest {

	@Autowired
	IncrementDigestQueueAppearancesUseCase increment;
	@Autowired
	JdbcTemplate jdbc;

	private long enqueue() {
		return jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status, created_at, last_seen_at)
				values ('UNCLASSIFIED', '{}'::jsonb, 'PENDING', now(), now()) returning id
				""", Long.class);
	}

	private int appearancesOf(long id) {
		return jdbc.queryForObject("select digest_appearances from review_queue_item where id = ?", Integer.class, id);
	}

	@Test
	void incrementsOnlyTheGivenIds() {
		long touched = enqueue();
		long untouched = enqueue();

		int rows = increment.increment(List.of(touched));

		assertThat(rows).isEqualTo(1);
		assertThat(appearancesOf(touched)).isEqualTo(1);
		assertThat(appearancesOf(untouched)).isEqualTo(0);
	}

	@Test
	void incrementsAgainOnASecondCall() {
		long id = enqueue();

		increment.increment(List.of(id));
		increment.increment(List.of(id));

		assertThat(appearancesOf(id)).isEqualTo(2);
	}

	@Test
	void anUnknownIdTouchesNothingAndDoesNotThrow() {
		int rows = increment.increment(List.of(999_999_999L));

		assertThat(rows).isZero();
	}
}
