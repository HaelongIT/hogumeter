package dev.hogumeter.core.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BM-02 AC-2 — 조건부 가격의 <b>태그를 원문에서 딜로 끌어올린다.</b>
 *
 * <p>확정본은 분포를 as-posted로 두라고 한다: "890,000이 그대로 분포 입력이 되고, applied_conditions에
 * 태그(카드명)만 남는다. base_price 역산 시도는 없다." 분포는 옳았다. <b>남아야 할 태그가 안 남았다.</b>
 *
 * <p>collector는 조건 태그(`카할`·`유료배송(금액미상)`·`조건부무료배송:와우무배`)를 뽑아
 * {@code raw_deal_post.raw._derived.applied_conditions}에 싣는다 — 담을 컬럼이 raw_deal_post에 없어서다
 * (docs/91 Q-46). 그런데 {@code deal_event.applied_conditions}는 V1에 있으면서 <b>아무도 쓰지 않았다.</b>
 * 골든 실측으로 뽐뿌 9.5% · 펨코 15%가 조건부인데, 딜에 도달하면 전부 무조건 가격의 얼굴을 하고 있었다.
 *
 * <p><b>왜 JPA가 아니라 SQL인가</b>: {@code DealEventEntity}는 이 컬럼을 매핑하지 않고(javadoc에 "미매핑"이라
 * 적혀 있다), {@code IngestDealsUseCase}와 함께 상대 개발자 소유다. 고치지 않는다. 딜은 이미 만들어져 있고
 * {@code deal_event_source}가 원문을 가리키므로, 네이티브 SQL 한 문장이면 엔티티를 건드리지 않고 태그를 옮긴다.
 *
 * <p><b>멱등</b>: 값이 이미 같으면 한 행도 건드리지 않고 {@code 0}을 반환한다. 매 틱 도는 단계라
 * 그러지 않으면 카운터(OBS-02)가 영원히 같은 수를 뱉으며 "일하는 척"한다.
 *
 * <p><b>여기서 끝이 아니다</b>: 이 값을 읽어 화면·알림에 "조건부"라고 쓰는 일은 {@code BenchmarkView}·
 * {@code GetBenchmarkUseCase}(상대 소유)의 몫이다. 지금은 파이프라인 로그(`conditional=`)만이 소비자다.
 * 태그를 보존하되 표시하지 않는 상태는 여전히 절반이다 — docs/91 Q-46에 남겨 둔다.
 */
@Service
public class PreserveAppliedConditionsUseCase {

	/**
	 * 병합된 딜은 원문이 여러 개다. 한 원문만 조건부여도 그 딜의 가격은 조건부이므로 <b>합집합</b>을 취한다.
	 *
	 * <p><b>정렬은 {@code collate "C"}(바이트 순서)로 못박는다.</b> 기본 정렬은 서버 로케일이 정한다 —
	 * 실측(postgres:16)에서 한글 태그가 코드포인트 순서와 다르게 나왔다. 로케일이 다른 DB로 옮기면 같은
	 * 태그 집합이 다른 배열이 되고, 아래 {@code is distinct from}이 매 틱 참이 되어 <b>멱등성이 조용히
	 * 깨진다</b>(일하는 척하는 카운터). 정렬 키가 환경에 의존하면 그건 값의 일부가 아니다.
	 *
	 * <p>{@code is distinct from}은 NULL을 값처럼 비교한다({@code =}는 NULL이면 NULL이라 절대 참이 아니다).
	 * 태그가 없는 딜은 NULL로 남긴다 — "태그 없음"을 빈 배열로 쓰지 않는다(값 없음을 값으로 표현하지 않는다).
	 */
	private static final String PRESERVE_SQL = """
			update deal_event deal
			   set applied_conditions = tagged.conditions
			  from (select source.deal_event_id as deal_id,
			               array(select tag
			                       from (select distinct tag
			                               from deal_event_source inner_source
			                               join raw_deal_post post
			                                 on post.id = inner_source.raw_deal_post_id
			                               cross join lateral jsonb_array_elements_text(
			                                            post.raw -> '_derived' -> 'applied_conditions') as tag
			                              where inner_source.deal_event_id = source.deal_event_id) unique_tags
			                      order by tag collate "C") as conditions
			          from deal_event_source source
			         group by source.deal_event_id) tagged
			 where deal.id = tagged.deal_id
			   and cardinality(tagged.conditions) > 0
			   and deal.applied_conditions is distinct from tagged.conditions
			""";

	private final JdbcTemplate jdbc;

	public PreserveAppliedConditionsUseCase(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** @return 태그가 새로 붙거나 바뀐 딜의 수. 변화가 없으면 0. */
	@Transactional
	public int preserveTags() {
		return jdbc.update(PRESERVE_SQL);
	}
}
