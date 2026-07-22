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

/**
 * 전역 제외 키워드(Q-28 ①). <b>{@code global_setting}은 V1부터 있었으나 엔티티도 REST도 없어 완전히 죽어
 * 있던 테이블</b>이다 — 여기서 생산자·소비처가 동시에 생겨 살아난다.
 *
 * <p>@Transactional로 tx 롤백(컨테이너 공유).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GlobalExcludeKeywordsTest {

	@Autowired
	GlobalExcludeKeywords globalKeywords;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void absentSettingMeansNoExclusion() {
		// 부재를 "전부 제외"로 읽지 않는다 — 설정이 없으면 아무것도 안 뺀다.
		assertThat(globalKeywords.keywords()).isEmpty();
	}

	@Test
	void replaceRoundTripsAndNormalizesLikePerProduct() {
		List<String> saved = globalKeywords.replace(List.of(" 리퍼 ", "중고", "  ", "리퍼"));

		// 정규화 정본(ExcludeKeywordPolicy.normalize)과 같은 규칙: 공백 다듬기·빈 값 탈락·중복 접기
		assertThat(saved).containsExactly("리퍼", "중고");
		assertThat(globalKeywords.keywords()).containsExactly("리퍼", "중고");
	}

	@Test
	void replaceIsFullReplacementNotAccumulation() {
		globalKeywords.replace(List.of("리퍼"));
		globalKeywords.replace(List.of("벌크"));

		assertThat(globalKeywords.keywords()).containsExactly("벌크");
	}

	@Test
	void malformedStoredValueFallsBackToEmptyInsteadOfKillingEveryQuery() {
		// 손으로 편집해 배열이 아닌 값이 들어간 경우. 기준가·신호·알림 전 표면이 이 조회를 타므로
		// 통째로 던지면 화면이 다 죽는다 — 빈 목록(제외 없음)으로 떨어뜨린다.
		jdbc.update("""
				insert into global_setting (key, value) values ('exclude_keywords', '"배열아님"'::jsonb)
				on conflict (key) do update set value = excluded.value
				""");

		assertThat(globalKeywords.keywords()).isEmpty();
	}
}
