package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationEntity;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.application.FoldUsedListingsUseCase.FoldReport;
import dev.hogumeter.core.application.port.out.UsedAlertMessage;
import dev.hogumeter.core.application.port.out.UsedAlertMessage.UsedAlertKind;
import dev.hogumeter.core.application.port.out.UsedAlertSender;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.used.ListingStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-02 목록 스냅샷 접기(Testcontainers). {@code ListingDiff}의 <b>첫 소비자</b>다 — 이 테스트가
 * 생기기 전 그 순수 함수는 호출자가 0이었고 {@code listing}은 아무도 읽지 않는 테이블이었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FoldUsedListingsUseCaseTest {

	private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
	private static final Instant T2 = T1.plus(Duration.ofMinutes(10));
	private static final Instant T3 = T2.plus(Duration.ofMinutes(10));

	@Autowired
	FoldUsedListingsUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	UsedListingObservationRepository observations;
	@Autowired
	ListingRepository listings;
	@Autowired
	RecordingUsedAlertSender alerts;

	private Long searchId;

	@BeforeEach
	void setUp() {
		alerts.sent.clear(); // 스파이는 컨텍스트 스코프라 테스트 간에 남는다
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		searchId = searches.save(new UsedSearchEntity(product.getId(), "BUNJANG", List.of("아이폰17"),
				List.of("케이스"), 700_000L, 10)).getId();
	}

	private void observe(String listingId, String title, long price, Instant at) {
		observations.save(new UsedListingObservationEntity(searchId, listingId, title, price, at));
	}

	/** 단건 조회는 프로덕션에 필요 없어 리포지토리에 두지 않는다 — 테스트가 걸러 쓴다. */
	private Optional<ListingEntity> listing(String listingId) {
		return listings.findByUsedSearchId(searchId).stream()
				.filter(l -> l.getListingId().equals(listingId))
				.findFirst();
	}

	@Test
	@DisplayName("첫 스냅샷의 매물은 ACTIVE로 새로 선다")
	void firstSnapshotCreatesActiveListings() {
		observe("a1", "아이폰 17 256 미개봉", 900_000L, T1);
		observe("a2", "아이폰 17 256 S급", 850_000L, T1);

		FoldReport report = useCase.foldPending();

		assertThat(report.batches()).isEqualTo(1);
		assertThat(report.appeared()).isEqualTo(2);
		assertThat(listings.findByUsedSearchId(searchId)).hasSize(2);
		assertThat(listing("a1")).get().satisfies(l -> {
			assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(l.getPrice()).isEqualTo(900_000L);
			assertThat(l.getFirstSeen()).isEqualTo(T1);
			assertThat(l.getLastSeen()).isEqualTo(T1);
		});
	}

	@Test
	@DisplayName("가격이 내려가면 그 사실이 매물에 반영된다 — 중고는 그 사이가 곧 정보다")
	void priceDropIsFolded() {
		observe("a1", "아이폰 17 256 미개봉", 900_000L, T1);
		observe("a1", "아이폰 17 256 미개봉", 820_000L, T2);

		FoldReport report = useCase.foldPending();

		assertThat(report.priceChanged()).isEqualTo(1);
		assertThat(listing("a1")).get().satisfies(l -> {
			assertThat(l.getPrice()).isEqualTo(820_000L);
			assertThat(l.getFirstSeen()).isEqualTo(T1); // 처음 본 시각은 안 바뀐다
			assertThat(l.getLastSeen()).isEqualTo(T2);
		});
	}

	@Test
	@DisplayName("목록에서 사라지면 판매완료로 추정한다(AC-9)")
	void disappearedListingIsMarkedSold() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		observe("a2", "아이폰 17 512", 990_000L, T1);
		observe("a2", "아이폰 17 512", 990_000L, T2); // a1만 빠진 스냅샷

		useCase.foldPending();

		assertThat(listing("a1")).get().extracting(ListingEntity::getStatus).isEqualTo(ListingStatus.SOLD);
		assertThat(listing("a2")).get().extracting(ListingEntity::getStatus).isEqualTo(ListingStatus.ACTIVE);
	}

	/**
	 * 핵심. 최신 배치만 접으면 T2의 가격 인하가 통째로 사라져 "900,000원짜리가 그대로 있다"로 보인다.
	 * 배치를 <b>건너뛰지 않는다</b>는 계약을 못박는다.
	 */
	@Test
	@DisplayName("사이의 배치를 건너뛰지 않는다 — 중간 스냅샷의 변동이 사라지면 안 된다")
	void everyBatchIsFoldedInOrderNotJustTheLatest() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		observe("a1", "아이폰 17 256", 820_000L, T2);
		observe("a1", "아이폰 17 256", 810_000L, T3);

		FoldReport report = useCase.foldPending();

		assertThat(report.batches()).isEqualTo(3);
		assertThat(report.priceChanged()).isEqualTo(2); // T1→T2, T2→T3 둘 다 보였다
		assertThat(listing("a1")).get().extracting(ListingEntity::getPrice).isEqualTo(810_000L);
	}

	@Test
	@DisplayName("이미 접은 배치를 다시 접지 않는다(워터마크)")
	void alreadyFoldedBatchesAreNotRefolded() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		useCase.foldPending();

		FoldReport second = useCase.foldPending();

		assertThat(second.batches()).isZero();
		assertThat(second.appeared()).isZero();
	}

	/**
	 * 소실만 있는 배치는 어떤 listing의 {@code last_seen}도 밀지 못한다 — 워터마크를 파생값으로 뒀다면
	 * 이 배치를 영원히 다시 접는다. 컬럼으로 둔 이유를 못박는다.
	 */
	@Test
	@DisplayName("소실만 있는 배치도 워터마크를 민다")
	void aBatchThatOnlyRemovesListingsStillAdvancesTheWatermark() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		observe("a2", "아이폰 17 512", 990_000L, T2); // a1 소실 + a2 신규
		useCase.foldPending();

		assertThat(useCase.foldPending().batches()).isZero();
		assertThat(searches.findById(searchId)).get()
				.extracting(UsedSearchEntity::getListingsFoldedThrough).isEqualTo(T2);
	}

	/**
	 * 끌올·재등록. 자연키가 UNIQUE라 새 행을 만들 수 없다 — 되살리되 처음 본 시각은 지어내지 않는다.
	 */
	@Test
	@DisplayName("사라졌던 매물이 다시 뜨면 같은 행을 되살리고 first_seen은 보존한다")
	void reappearedListingRevivesTheSameRow() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		observe("a2", "아이폰 17 512", 990_000L, T2); // a1 소실 → SOLD
		observe("a1", "아이폰 17 256", 880_000L, T3); // a1 재등장
		observe("a2", "아이폰 17 512", 990_000L, T3);

		FoldReport report = useCase.foldPending();

		assertThat(report.revived()).isEqualTo(1);
		assertThat(listings.findByUsedSearchId(searchId)).hasSize(2); // 새 행이 생기지 않는다
		assertThat(listing("a1")).get().satisfies(l -> {
			assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
			assertThat(l.getPrice()).isEqualTo(880_000L);
			assertThat(l.getFirstSeen()).isEqualTo(T1); // 처음 본 시각을 지어내지 않는다
			assertThat(l.getLastSeen()).isEqualTo(T3);
		});
	}

	@Test
	@DisplayName("변한 것 없이 그대로 있어도 마지막 목격 시각은 민다")
	void unchangedListingStillAdvancesLastSeen() {
		observe("a1", "아이폰 17 256", 900_000L, T1);
		observe("a1", "아이폰 17 256", 900_000L, T2);

		useCase.foldPending();

		assertThat(listing("a1")).get().extracting(ListingEntity::getLastSeen).isEqualTo(T2);
	}

	@Test
	@DisplayName("관측이 없으면 아무 일도 일어나지 않는다")
	void noObservationsFoldsNothing() {
		assertThat(useCase.foldPending()).isEqualTo(FoldReport.empty());
	}

	// ── USED-03 생애주기 알림(AC-7·8·9) ────────────────────────────────
	// 이 테스트들이 생기기 전 `UsedMatcher`·`UsedAlertPolicy`·`UsedSearchSpec`은 **프로덕션 호출자가 0**
	// 이었다 — 순수 함수 셋이 GREEN인 채로 죽어 있었고, 등록한 조건검색은 아무 알림도 낳지 않았다.

	@Test
	@DisplayName("AC-7: 필터·목표가를 통과한 신규 매물만 알림하고 그 매물을 승격한다")
	void newListingUnderTargetPriceAlertsAndPromotes() {
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T1); // 목표가 700,000 이하
		observe("a2", "아이폰 17 256 S급", 850_000L, T1); // 목표가 초과 → 알림 없음
		observe("a3", "아이폰 17 256 케이스 포함", 500_000L, T1); // exclude 히트 → 알림 없음

		FoldReport report = useCase.foldPending();

		assertThat(report.alertsNew()).isEqualTo(1);
		assertThat(alerts.kinds()).containsExactly(UsedAlertKind.NEW);
		assertThat(alerts.sent.get(0).price()).isEqualTo(680_000L);
		assertThat(listing("a1")).get().extracting(ListingEntity::isPromoted).isEqualTo(true);
		assertThat(listing("a2")).get().extracting(ListingEntity::isPromoted).isEqualTo(false);
		assertThat(listing("a3")).get().extracting(ListingEntity::isPromoted).isEqualTo(false);
	}

	@Test
	@DisplayName("AC-8: 승격된 매물의 가격 하락만 후속 알림한다 — 미승격은 조용하다")
	void priceDropAlertsOnlyForPromotedListings() {
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T1); // 승격됨
		observe("a2", "아이폰 17 256 S급", 850_000L, T1); // 미승격
		observe("a1", "아이폰 17 256 미개봉", 650_000L, T2);
		observe("a2", "아이폰 17 256 S급", 800_000L, T2);

		FoldReport report = useCase.foldPending();

		assertThat(report.alertsPriceDrop()).isEqualTo(1);
		assertThat(alerts.kinds()).containsExactly(UsedAlertKind.NEW, UsedAlertKind.PRICE_DROP);
		assertThat(alerts.sent.get(1).previousPrice()).isEqualTo(680_000L);
		assertThat(alerts.sent.get(1).price()).isEqualTo(650_000L);
	}

	@Test
	@DisplayName("AC-8 거울상: 가격이 오르면 승격 매물이어도 알림하지 않는다(스팸 방지)")
	void priceRiseNeverAlerts() {
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T1);
		observe("a1", "아이폰 17 256 미개봉", 690_000L, T2);

		FoldReport report = useCase.foldPending();

		assertThat(report.alertsPriceDrop()).isZero();
		assertThat(alerts.kinds()).containsExactly(UsedAlertKind.NEW);
	}

	@Test
	@DisplayName("AC-9: 승격된 매물의 소실만 알림한다 — 스냅샷 전체엔 미적용")
	void soldOutAlertsOnlyForPromotedListings() {
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T1); // 승격
		observe("a2", "아이폰 17 256 S급", 850_000L, T1); // 미승격
		observe("a3", "아이폰 17 512 미개봉", 690_000L, T2); // a1·a2 둘 다 소실

		FoldReport report = useCase.foldPending();

		assertThat(report.alertsSoldOut()).isEqualTo(1); // a1만
		assertThat(alerts.kinds()).contains(UsedAlertKind.SOLD_OUT);
		assertThat(alerts.sent.stream().filter(m -> m.kind() == UsedAlertKind.SOLD_OUT).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("AC-10: 끌올(같은 listingId 재등장)은 신규가 아니라 알림도 없다")
	void reappearingListingDoesNotAlertAgain() {
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T1); // NEW 알림 1회
		observe("a2", "아이폰 17 512", 990_000L, T2); // a1 소실 → SOLD(승격이라 SOLD_OUT 알림)
		observe("a1", "아이폰 17 256 미개봉", 680_000L, T3); // 재등장
		observe("a2", "아이폰 17 512", 990_000L, T3);

		FoldReport report = useCase.foldPending();

		assertThat(report.alertsNew()).isEqualTo(1); // 재등장은 NEW로 세지 않는다
		assertThat(alerts.kinds()).containsExactly(UsedAlertKind.NEW, UsedAlertKind.SOLD_OUT);
	}

	@Test
	@DisplayName("알림엔 원문 링크가 실린다 — 판단은 사람이 원문에서 한다")
	void alertCarriesTheSourceUrl() {
		observations.save(new UsedListingObservationEntity(searchId, "a1", "아이폰 17 256 미개봉", 680_000L, T1,
				"https://m.bunjang.co.kr/products/a1"));

		useCase.foldPending();

		assertThat(alerts.sent).singleElement().satisfies(m -> {
			assertThat(m.url()).isEqualTo("https://m.bunjang.co.kr/products/a1");
			assertThat(m.productName()).isEqualTo("아이폰 17"); // 대상이 무엇인지 이름으로 말한다
		});
		assertThat(listing("a1")).get().extracting(ListingEntity::getUrl)
				.isEqualTo("https://m.bunjang.co.kr/products/a1");
	}

	@TestConfiguration
	static class RecordingUsedSenderConfig {

		@Bean
		@Primary
		RecordingUsedAlertSender recordingUsedAlertSender() {
			return new RecordingUsedAlertSender();
		}
	}

	/** 발송 대신 기록하는 스파이 — "무엇이 나갔는가"를 값으로 검증한다(문구가 아니라). */
	static class RecordingUsedAlertSender implements UsedAlertSender {

		final List<UsedAlertMessage> sent = new ArrayList<>();

		@Override
		public void sendUsed(UsedAlertMessage message) {
			sent.add(message);
		}

		List<UsedAlertKind> kinds() {
			return sent.stream().map(UsedAlertMessage::kind).toList();
		}
	}
}
