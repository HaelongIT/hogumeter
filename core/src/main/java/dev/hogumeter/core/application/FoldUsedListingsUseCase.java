package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationEntity;
import dev.hogumeter.core.adapter.persistence.UsedListingObservationRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.used.ListingDiff;
import dev.hogumeter.core.domain.used.ListingDiffResult;
import dev.hogumeter.core.domain.used.ListingStatus;
import dev.hogumeter.core.domain.used.ObservedListing;
import dev.hogumeter.core.domain.used.PriceChange;
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

	public FoldUsedListingsUseCase(UsedSearchRepository searches, UsedListingObservationRepository observations,
			ListingRepository listings) {
		this.searches = searches;
		this.observations = observations;
		this.listings = listings;
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
			for (Instant batchAt : pending) {
				foldOneBatch(search, batchAt, tally);
			}
		}
		return tally.toReport();
	}

	private void foldOneBatch(UsedSearchEntity search, Instant batchAt, Tally tally) {
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
				listings.save(new ListingEntity(search.getId(), o.getListingId(), o.getTitle(), o.getPrice(),
						batchAt));
				tally.appeared++;
			}
			else {
				// 종착 상태였는데 목록에 다시 떴다(재등록·복구). 자연키가 UNIQUE라 새 행을 만들 수 없고,
				// 만들어서도 안 된다 — 같은 행을 되살리되 first_seen은 보존한다.
				existing.revive(o.getTitle(), o.getPrice(), batchAt);
				tally.revived++;
			}
		}

		for (PriceChange change : result.priceChanged()) {
			UsedListingObservationEntity o = observed.get(change.listingId());
			knownById.get(change.listingId()).observedAgain(o.getTitle(), o.getPrice(), batchAt);
			tally.priceChanged++;
		}

		for (String goneId : result.disappeared()) {
			knownById.get(goneId).disappeared();
			tally.disappeared++;
		}

		// 변한 것 없이 그대로 있던 매물도 "이번에도 봤다"를 남긴다 — 안 밀면 마지막 목격 시각이 굳어
		// 나중에 "언제까지 살아 있었나"에 거짓으로 답한다.
		for (ObservedListing seen : current) {
			ListingEntity listing = knownById.get(seen.listingId());
			if (listing != null && listing.getStatus() == ListingStatus.ACTIVE) {
				UsedListingObservationEntity o = observed.get(seen.listingId());
				listing.observedAgain(o.getTitle(), o.getPrice(), batchAt);
			}
		}

		search.foldedThrough(batchAt);
		tally.batches++;
	}

	/** 한 회 접기가 무엇을 했는가(OBS-02). 부류가 다른 사실을 한 카운터로 합치지 않는다. */
	public record FoldReport(int batches, int appeared, int revived, int priceChanged, int disappeared) {

		public static FoldReport empty() {
			return new FoldReport(0, 0, 0, 0, 0);
		}
	}

	private static final class Tally {
		int batches;
		int appeared;
		int revived;
		int priceChanged;
		int disappeared;

		FoldReport toReport() {
			return new FoldReport(batches, appeared, revived, priceChanged, disappeared);
		}
	}
}
