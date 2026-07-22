package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.deal.ExcludeKeywordPolicy;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 전역 제외 키워드(Q-28 ①) — <b>모든 variant에 함께 적용</b>되는 제외 키워드. per-product 정책
 * ({@code alert_policy.exclude_keywords})과 합쳐져 표본을 거른다({@link VariantExcludeKeywords}).
 *
 * <p><b>왜 전역이 필요한가</b>: "리퍼"·"중고"·"파손" 같은 노이즈는 <b>어느 제품에나</b> 같은 뜻이다.
 * 제품마다 다시 적으면 새 제품을 등록할 때마다 같은 목록을 옮겨 적어야 하고, 한 곳을 빠뜨리면 그 제품만
 * 조용히 오염된다. 전역에 한 번 적고, 제품별 목록은 그 제품에만 있는 노이즈에 쓴다.
 *
 * <p><b>저장은 {@code global_setting}</b>(key/value jsonb) — V1부터 있었으나 <b>엔티티도 REST도 없어
 * 완전히 죽어 있던 테이블</b>이다(docs/91 Q-28 ①, 죽은 컬럼 게이트가 {@code updated_at}을 잡고 있었다).
 * 여기서 생산자·소비처가 동시에 생겨 살아난다.
 *
 * <p><b>왜 엔티티가 아니라 네이티브 SQL인가</b>: {@code value}는 설정마다 모양이 다른 generic jsonb다.
 * 엔티티로 특정 타입(예: {@code List<String>})에 묶으면 다른 설정을 넣는 순간 깨진다 — 키별 해석을
 * 각 서비스가 하고, 저장은 KV로 둔다(엔티티가 매핑하지 않는 컬럼은 네이티브 SQL로 다룬다).
 *
 * <p><b>정규화 정본은 하나다</b>: {@link ExcludeKeywordPolicy#normalize} — per-product와 같은 규칙을 써야
 * 합칠 때 공백·중복으로 어긋나지 않는다.
 */
@Service
public class GlobalExcludeKeywords {

	/** {@code global_setting}의 키. 값은 JSON 문자열 배열(["리퍼","중고"]). */
	static final String KEY = "exclude_keywords";

	private final JdbcTemplate jdbc;
	private final JsonMapper json = new JsonMapper();

	public GlobalExcludeKeywords(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 전역 제외 키워드. 설정이 없으면 <b>빈 목록</b>(제외 없음) — 부재를 "모든 걸 제외"로 읽지 않는다.
	 * 저장된 값이 깨졌으면(수동 편집 등) 빈 목록으로 떨어진다 — 조회가 통째로 죽는 것보다 낫다(전 표면이 이걸 탄다).
	 */
	public List<String> keywords() {
		List<String> raw = jdbc.queryForList(
				"select value #>> '{}' from global_setting where key = ?", String.class, KEY);
		if (raw.isEmpty() || raw.get(0) == null) {
			return List.of();
		}
		try {
			return ExcludeKeywordPolicy.normalize(json.readValue(raw.get(0), List.class));
		}
		catch (RuntimeException malformed) {
			return List.of();
		}
	}

	/**
	 * 전역 제외 키워드를 <b>통째로 교체</b>한다(부분 갱신 없음 — 화면이 전체 목록을 보내는 것과 같은 계약).
	 * 정규화는 정본 규칙을 따른다. {@code updated_at}은 DB가 갱신한다.
	 */
	@Transactional
	public List<String> replace(List<String> keywords) {
		List<String> normalized = ExcludeKeywordPolicy.normalize(keywords);
		jdbc.update("""
				insert into global_setting (key, value, updated_at) values (?, ?::jsonb, now())
				on conflict (key) do update set value = excluded.value, updated_at = now()
				""", KEY, json.writeValueAsString(normalized));
		return normalized;
	}
}
