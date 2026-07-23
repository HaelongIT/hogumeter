package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationEntity;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.application.port.out.UsedAlertMessage;
import dev.hogumeter.core.application.port.out.UsedAlertMessage.UsedAlertKind;
import dev.hogumeter.core.application.port.out.UsedAlertSender;
import dev.hogumeter.core.domain.used.BonusGroup;
import dev.hogumeter.core.domain.used.ListingDiff;
import dev.hogumeter.core.domain.used.ListingDiffResult;
import dev.hogumeter.core.domain.used.ListingStatus;
import dev.hogumeter.core.domain.used.ObservedListing;
import dev.hogumeter.core.domain.used.PriceChange;
import dev.hogumeter.core.domain.used.UsedAlertPolicy;
import dev.hogumeter.core.domain.used.UsedSearchSpec;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-02 목록 스냅샷 접기 — collector가 쌓은 {@code used_listing_observation} 배치를 순서대로
 * {@link ListingDiff}에 먹여 {@code listing} 생애주기를 갱신한다.
 *
 * <p>이 유스케이스가 생기기 전까지 {@code ListingDiff}는 <b>호출자가 0</b>이었고 {@code listing}·
 * {@code used_listing_observation}은 아무도 읽지 않는 테이블이었다 — 전부 GREEN인 채로.
 *
 * <p><b>배치를 건너뛰지 않는다.</b> 워터마크 이후의 스냅샷을 시각 오름차순으로 하나씩 접는다. 최신 하나만
 * 보면 그 사이의 가격 변동과 소실이 조용히 사라진다 — 중고는 그 사이가 곧 정보다(내렸다 팔렸다).
 */
@Service
public class FoldUsedListingsUseCase {

	private final UsedSearchRepository searches;
	private final UsedListingObservationRepository observations;
	private final ListingRepository listings;
	private final UsedSearchBonusGroupRepository bonusGroups;
	private final ProductRepository products;
	private final UsedAlertSender alertSender;

	public FoldUsedListingsUseCase(UsedSearchRepository searches, UsedListingObservationRepository observations,
			ListingRepository listings, UsedSearchBonusGroupRepository bonusGroups, ProductRepository products,
			UsedAlertSender alertSender) {
		this.searches = searches;
		this.observations = observations;
		this.listings = listings;
		this.bonusGroups = bonusGroups;
		this.products = products;
		this.alertSender = alertSender;
	}

	/** 모든 검색의 미처리 배치를 접는다. 반환값은 이번에 접은 <b>배치 수</b>(0이면 새 스냅샷이 없었다). */
	@Transactional
	public FoldReport foldPending() {
		Tally tally = new Tally();
		for (UsedSearchEntity search : searches.findAll()) {
			Instant watermark = search.getListingsFoldedThrough();
			List<Instant> pending = watermark == null
					? observations.findAllBatchTimes(search.getId())
					: observations.findBatchTimesAfter(search.getId(), watermark);
			if (pending.isEmpty()) {
				continue; // 접을 게 없으면 스펙·이름 조회도 하지 않는다
			}
			AlertContext context = contextFor(search);
			for (Instant batchAt : pending) {
				foldOneBatch(search, batchAt, tally, context);
			}
		}
		return tally.toReport();
	}

	/**
	 * 검색당 한 번만 조립한다 — 3계층 스펙은 배치마다 변하지 않는다. 제품 이름은 알림이 "무엇에 대한
	 * 알림인가"를 말하기 위한 것이고, 못 찾으면 null이라 어댑터가 "대상 미상"으로 그린다(지어내지 않는다).
	 */
	private AlertContext contextFor(UsedSearchEntity search) {
		List<BonusGroup> groups = bonusGroups.findByUsedSearchId(search.getId()).stream()
				.map(g -> new BonusGroup(g.getKeywords(), g.getMode()))
				.toList();
		UsedSearchSpec spec = new UsedSearchSpec(search.getRequiredKeywords(), groups,
				search.getExcludeKeywords());
		String productName = products.findById(search.getProductId()).map(ProductEntity::getName).orElse(null);
		return new AlertContext(spec, search.getTargetPrice(), productName);
	}

	private void foldOneBatch(UsedSearchEntity search, Instant batchAt, Tally tally, AlertContext context) {
		List<UsedListingObservationEntity> batch = observations
				.findByUsedSearchIdAndObservedAt(search.getId(), batchAt);

		Map<String, UsedListingObservationEntity> observed = new HashMap<>();
		for (UsedListingObservationEntity o : batch) {
			observed.put(o.getListingId(), o); // 같은 배치의 중복은 마지막 관측 승리(diff와 같은 규약)
		}

		List<ListingEntity> known = listings.findByUsedSearchId(search.getId());
		Map<String, ListingEntity> knownById = new HashMap<>();
		known.forEach(l -> knownById.put(l.getListingId(), l));

		// diff의 "이전 스냅샷" = 지금 살아 있다고 아는 매물. 종착(SOLD·REMOVED)은 이전 목록에 없었던 것으로
		// 본다 — 그래야 재등장이 appeared로 잡혀 되살릴 기회를 얻는다.
		List<ObservedListing> previous = known.stream()
				.filter(l -> l.getStatus() == ListingStatus.ACTIVE)
				.map(l -> new ObservedListing(l.getListingId(), l.getPrice()))
				.toList();
		List<ObservedListing> current = batch.stream()
				.map(o -> new ObservedListing(o.getListingId(), o.getPrice()))
				.toList();

		ListingDiffResult result = ListingDiff.diff(previous, current);

		for (ObservedListing appeared : result.appeared()) {
			UsedListingObservationEntity o = observed.get(appeared.listingId());
			ListingEntity existing = knownById.get(appeared.listingId());
			if (existing == null) {
				ListingEntity created = listings.save(new ListingEntity(search.getId(), o.getListingId(),
						o.getTitle(), o.getPrice(), batchAt, o.getUrl()));
				knownById.put(created.getListingId(), created);
				tally.appeared++;
				// AC-7: 3계층 필터 + 목표가를 통과한 **신규** 매물만 알린다. 알린 매물은 승격되고,
				// 그 표식이 이후 후속 알림(AC-8·9)의 자격이 된다.
				if (UsedAlertPolicy.shouldAlertOnNew(o.getTitle(), o.getPrice(), context.spec(),
						context.targetPrice())) {
					created.promote();
					alertSender.sendUsed(new UsedAlertMessage(UsedAlertKind.NEW, context.productName(),
							o.getTitle(), o.getPrice(), null, o.getUrl()));
					tally.alertsNew++;
				}
			}
			else {
				// 종착 상태였는데 목록에 다시 떴다(재등록·복구). 자연키가 UNIQUE라 새 행을 만들 수 없고,
				// 만들어서도 안 된다 — 같은 행을 되살리되 first_seen은 보존한다.
				// **알리지 않는다**(AC-10): 같은 자연키는 신규가 아니다. 승격 표식은 그대로 남아 이후
				// 가격하락·소실은 다시 알린다. "재등장 자체가 소식인가"는 실사용에서 판단(docs/91 Q-75).
				existing.revive(o.getTitle(), o.getPrice(), batchAt);
				existing.observedUrl(o.getUrl());
				tally.revived++;
			}
		}

		for (PriceChange change : result.priceChanged()) {
			UsedListingObservationEntity o = observed.get(change.listingId());
			ListingEntity listing = knownById.get(change.listingId());
			// 알림 판정은 **갱신 전에** 한다 — 갱신하면 이전 가격이 사라져 "얼마에서 얼마로"를 못 말한다.
			boolean alerts = UsedAlertPolicy.shouldAlertOnPriceChange(change, listing.isPromoted());
			listing.observedAgain(o.getTitle(), o.getPrice(), batchAt);
			listing.observedUrl(o.getUrl());
			tally.priceChanged++;
			if (alerts) {
				alertSender.sendUsed(new UsedAlertMessage(UsedAlertKind.PRICE_DROP, context.productName(),
						o.getTitle(), change.currentPrice(), change.previousPrice(), listing.getUrl()));
				tally.alertsPriceDrop++;
			}
		}

		for (String goneId : result.disappeared()) {
			ListingEntity listing = knownById.get(goneId);
			listing.disappeared();
			tally.disappeared++;
			if (UsedAlertPolicy.shouldAlertOnSoldOut(listing.isPromoted())) {
				alertSender.sendUsed(new UsedAlertMessage(UsedAlertKind.SOLD_OUT, context.productName(),
						listing.getTitle(), listing.getPrice(), null, listing.getUrl()));
				tally.alertsSoldOut++;
			}
		}

		// 변한 것 없이 그대로 있던 매물도 "이번에도 봤다"를 남긴다 — 안 밀면 마지막 목격 시각이 굳어
		// 나중에 "언제까지 살아 있었나"에 거짓으로 답한다.
		for (ObservedListing seen : current) {
			ListingEntity listing = knownById.get(seen.listingId());
			if (listing != null && listing.getStatus() == ListingStatus.ACTIVE) {
				UsedListingObservationEntity o = observed.get(seen.listingId());
				listing.observedAgain(o.getTitle(), o.getPrice(), batchAt);
				listing.observedUrl(o.getUrl());
			}
		}

		search.foldedThrough(batchAt);
		tally.batches++;
	}

	/** 검색당 한 번 조립하는 알림 재료. 목표가는 null 가능(가격 조건 없음). */
	private record AlertContext(UsedSearchSpec spec, Long targetPrice, String productName) {
	}

	/**
	 * 한 회 접기가 무엇을 했는가(OBS-02). 부류가 다른 사실을 한 카운터로 합치지 않는다 —
	 * 특히 <b>관측한 사건 수</b>(appeared·priceChanged·disappeared)와 <b>알린 수</b>는 다르다.
	 * 둘의 차이가 곧 "필터가 얼마나 걸렀는가"다.
	 */
	public record FoldReport(int batches, int appeared, int revived, int priceChanged, int disappeared,
			int alertsNew, int alertsPriceDrop, int alertsSoldOut) {

		public static FoldReport empty() {
			return new FoldReport(0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final class Tally {
		int batches;
		int appeared;
		int revived;
		int priceChanged;
		int disappeared;
		int alertsNew;
		int alertsPriceDrop;
		int alertsSoldOut;

		FoldReport toReport() {
			return new FoldReport(batches, appeared, revived, priceChanged, disappeared,
					alertsNew, alertsPriceDrop, alertsSoldOut);
		}
	}
}
