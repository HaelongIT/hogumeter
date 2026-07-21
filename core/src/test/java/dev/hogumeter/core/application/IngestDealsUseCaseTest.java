package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductAxisEntity;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemEntity;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 3 수집 파이프라인 — 매칭→병합→deal_event 저장, 애매→리뷰큐(Testcontainers). */
@Import({ TestcontainersConfiguration.class, IngestDealsUseCaseTest.RecordingSenderConfig.class })
@SpringBootTest
@Transactional
class IngestDealsUseCaseTest {

	private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	IngestDealsUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	AliasRepository aliases;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	ReviewQueueItemRepository reviewQueue;
	@Autowired
	RecordingAlertSender recordingAlertSender;
	@Autowired
	DealAlertRepository dealAlerts;
	@Autowired
	ProductAxisRepository productAxes;

	private long variantId;
	private long colorProductId;
	private long colorVariantId;
	private int postSeq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		aliases.save(new AliasEntity(product.getId(), "아이폰17"));
		variantId = variant.getId();

		// 수요축(색상)을 등록한 제품 — 매칭이 제목에서 색을 판별해야 한다(Q-66 ①).
		ProductEntity colorful = products.save(new ProductEntity("갤럭시 25", "스마트폰", DemandAxisMode.SPLIT));
		colorProductId = colorful.getId();
		colorVariantId = variants.save(new VariantEntity(colorProductId, "256GB", Map.of("용량", "256GB"))).getId();
		productAxes.save(new ProductAxisEntity(colorProductId, AxisType.PRICE, "용량", List.of("256GB")));
		productAxes.save(new ProductAxisEntity(colorProductId, AxisType.DEMAND, "색상", List.of("블랙", "화이트")));

		recordingAlertSender.sent.clear(); // 스파이는 싱글톤 — @Transactional이 롤백하지 않으므로 매 케이스 초기화
	}

	private void savePost(String site, String title, Long price, Instant when) {
		rawPosts.save(new RawDealPost(site, "post" + postSeq++, "https://" + site + ".test/" + postSeq,
				title, price, when, when, "ACTIVE"));
	}

	@Test
	void confirmedMatchCreatesDealEvent() {
		savePost("ppomppu", "아이폰 17 256기가 자급제 89만", 890_000L, T);

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(1);
		assertThat(deals.get(0).getPriceFirst()).isEqualTo(890_000L);
		assertThat(sources.findByDealEventId(deals.get(0).getId())).hasSize(1);
	}

	@Test
	void secondSiteMergesIntoVerifiedDeal() {
		savePost("ppomppu", "아이폰 17 256기가 89만", 890_000L, T);
		savePost("ruliweb", "아이폰 17 256기가 특가", 895_000L, T.plus(Duration.ofHours(6)));

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(1); // 병합
		assertThat(deals.get(0).getStatus()).isEqualTo(DealStatus.VERIFIED);
		assertThat(sources.findByDealEventId(deals.get(0).getId())).hasSize(2); // 2사이트 소스
	}

	@Test
	void noPricePostIsSkipped() {
		savePost("ppomppu", "아이폰 17 256기가 팝니다 문의", null, T);

		useCase.ingestPending();

		assertThat(dealEvents.findByVariantId(variantId)).isEmpty();
	}

	@Test
	void ambiguousMatchEnqueuesReviewItem() {
		savePost("ppomppu", "애플 아이폰 신형 256기가", 800_000L, T); // "17" 없음 → CANDIDATE

		useCase.ingestPending();

		assertThat(dealEvents.findByVariantId(variantId)).isEmpty();
		assertThat(reviewQueue.findByType(ReviewQueueType.UNCLASSIFIED)).hasSize(1);
	}

	/**
	 * Q-66 ①: 수요축을 등록한 제품이면 매칭이 <b>제목에서 그 값을 판별해 딜에 싣는다</b>(확정본 §41).
	 * 컬럼이 있다는 것과 값이 도달한다는 것은 다르다 — 매칭→도메인→엔티티 다섯 구간 중 하나만 끊겨도
	 * 값은 조용히 사라진다. 그래서 DB까지 관통해 본다.
	 */
	@Test
	void demandAxisValueIsParsedFromTheTitleAndReachesTheDeal() {
		aliases.save(new AliasEntity(colorProductId, "갤럭시25"));
		savePost("ppomppu", "갤럭시 25 256기가 블랙 특가", 890_000L, T);

		useCase.ingestPending();

		DealEventEntity deal = dealEvents.findByVariantId(colorVariantId).get(0);
		assertThat(deal.getDemandAxisValue()).isEqualTo("블랙");
	}

	/** 제목이 색을 안 밝히면 <b>미상(null)</b>이다 — 기본값을 골라 담으면 그 분포가 조용히 오염된다. */
	@Test
	void demandAxisValueIsUnknownWhenTheTitleDoesNotSayIt() {
		aliases.save(new AliasEntity(colorProductId, "갤럭시25"));
		savePost("ppomppu", "갤럭시 25 256기가 특가", 890_000L, T);

		useCase.ingestPending();

		DealEventEntity deal = dealEvents.findByVariantId(colorVariantId).get(0);
		assertThat(deal.getDemandAxisValue()).isNull();
	}

	/**
	 * Q-66 ① E(확정본 §41): 분리 제품인데 색을 판별 못 한 딜은 <b>승격 큐(DEMAND_UNKNOWN)에 뜬다</b> —
	 * 분포에서 빠지는 것만으로는 부족하다(사람이 볼 수 없으면 유실). 값을 아는 딜은 큐에 안 뜬다.
	 */
	@Test
	void splitDealWithUnknownValueGoesToTheReviewQueue() {
		aliases.save(new AliasEntity(colorProductId, "갤럭시25"));
		savePost("ppomppu", "갤럭시 25 256기가 특가", 890_000L, T); // 색 미상
		savePost("ppomppu", "갤럭시 25 256기가 블랙 특가", 880_000L, T); // 색 판별됨

		useCase.ingestPending();

		List<ReviewQueueItemEntity> queued = reviewQueue.findByType(ReviewQueueType.DEMAND_UNKNOWN);
		assertThat(queued).as("미상 딜만 큐에 뜬다 — 블랙은 안 뜬다").hasSize(1);
	}

	/** 묶음(GROUPED) 제품은 색이 없어도 미상 큐에 올리지 않는다 — 어차피 한 분포라 값이 필요 없다. */
	@Test
	void groupedDealWithNoValueDoesNotGoToDemandUnknownQueue() {
		savePost("ppomppu", "아이폰 17 256기가 특가 89만", 890_000L, T); // variantId는 GROUPED 제품

		useCase.ingestPending();

		assertThat(reviewQueue.findByType(ReviewQueueType.DEMAND_UNKNOWN)).isEmpty();
	}

	@Test
	void repeatedIngestOfSameUnclassifiedFoldsIntoOneRow() {
		// Q-27④: 미상 원문은 딜로 링크되지 않아 findUnprocessed가 매 틱 다시 반환한다. 두 번 수집해도
		// 새 행을 만들지 않고 occurrences만 는다(무한 증가 방지 + Q-15 승격이 한 행만 처리하면 끝나게).
		savePost("ppomppu", "애플 아이폰 신형 256기가", 800_000L, T); // "17" 없음 → CANDIDATE

		useCase.ingestPending();
		useCase.ingestPending(); // 같은 미상 원문이 다시 스캔된다

		List<ReviewQueueItemEntity> items = reviewQueue.findByType(ReviewQueueType.UNCLASSIFIED);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).getOccurrences()).isEqualTo(2);
	}

	@Test
	void lowerOutlierIsFlaggedAndQueued() {
		// 병합 안 되는 5건(30k 간격 > 허용폭) + 1건 대박(이상치)
		for (long price : new long[] { 800_000, 830_000, 860_000, 890_000, 920_000 }) {
			savePost("ppomppu", "아이폰 17 256기가 특가", price, T);
		}
		savePost("ppomppu", "아이폰 17 256기가 특가", 100_000L, T); // 분포 대비 하향 이상치

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(6); // 전부 별개(간격이 허용폭 초과)
		DealEventEntity low = deals.stream().filter(d -> d.getPriceFirst() == 100_000L).findFirst().orElseThrow();
		assertThat(low.getOutlierFlag()).isEqualTo(OutlierFlag.LOWER);
		assertThat(reviewQueue.findByType(ReviewQueueType.OUTLIER_LOWER)).hasSize(1);
	}

	@Test
	void initiallySoldOutPostIsBornEndedAndNotAlerted() {
		// Q-27③: 최초 수집 시 이미 품절인 원문 → 딜은 ENDED로 태어나고 알림이 나가지 않는다.
		// (예전엔 ACTIVE로 태어나 evaluate가 알림을 낸 뒤 같은 틱에 ENDED로 자가치유 — 알림은 이미 나간 뒤였다.)
		rawPosts.save(new RawDealPost("ppomppu", "post" + postSeq++, "https://ppomppu.test/sold",
				"아이폰 17 256기가 특가 89만", 890_000L, T, T, "SOLD_OUT"));

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(1);
		assertThat(deals.get(0).getStatus()).isEqualTo(DealStatus.ENDED);
		assertThat(recordingAlertSender.sent).isEmpty();
	}

	@Test
	void firstAlertIsRecordedInHistoryWhenSent() {
		// 딜 1건 → SPARSE, 보유 최저가 잣대로 GOOD 알림이 실제로 나간다 → 이력에 FIRST(후속 알림의 전제, Q-67).
		savePost("ppomppu", "아이폰 17 256기가 특가 89만", 890_000L, T);

		useCase.ingestPending();

		DealEventEntity deal = dealEvents.findByVariantId(variantId).get(0);
		assertThat(recordingAlertSender.sent).hasSize(1);
		assertThat(dealAlerts.findAll())
				.extracting(DealAlertEntity::getDealEventId, DealAlertEntity::getKind)
				.containsExactly(tuple(deal.getId(), DealAlertEntity.FIRST));
	}

	@Test
	void reportCountsMatchTiersAndFirstAlerts() {
		// OBS-02(Q-57 ②③): 스냅샷 차이로는 못 세는 매칭 tier 분포·첫 알림 발송 수를 유스케이스가 직접 센다.
		savePost("ppomppu", "아이폰 17 256기가 특가 89만", 890_000L, T); // CONFIRMED + 첫 알림(딜 1건 → SPARSE GOOD)
		savePost("ppomppu", "애플 아이폰 신형 256기가", 800_000L, T);      // CANDIDATE("17" 없음 → 코어 토큰 부분일치)
		savePost("ppomppu", "아이폰 17 256기가 팝니다 문의", null, T);     // 가격 없음 → skip
		savePost("ppomppu", "무관한 텀블러 세트 판매", 20_000L, T);        // REJECTED(코어 토큰 무관)

		IngestReport report = useCase.ingestPending();

		assertThat(report.confirmed()).isEqualTo(1);
		assertThat(report.candidate()).isEqualTo(1);
		assertThat(report.unknown()).isZero();
		assertThat(report.rejected()).isEqualTo(1);
		assertThat(report.skippedNoPrice()).isEqualTo(1);
		assertThat(report.firstAlertsSent()).isEqualTo(1);
	}

	@TestConfiguration
	static class RecordingSenderConfig {
		@Bean
		@Primary
		RecordingAlertSender recordingAlertSender() {
			return new RecordingAlertSender();
		}
	}

	/** 발송 대신 기록하는 스파이 — "알림이 나갔는가"를 통관 검증(@Primary로 StubAlertSender 대체). */
	static class RecordingAlertSender implements AlertSender {
		final List<AlertMessage> sent = new ArrayList<>();

		@Override
		public void send(AlertMessage message) {
			sent.add(message);
		}
	}
}
