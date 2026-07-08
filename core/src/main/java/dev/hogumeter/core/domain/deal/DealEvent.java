package dev.hogumeter.core.domain.deal;

import java.time.Instant;
import java.util.Set;

/**
 * 병합된 하나의 딜(순수 값 타입/집계). BM-06 기준가 산출의 입력이자 BM-04 병합·상태기계의 대상.
 * 대표가 = priceFirst(분포 입력은 일관되게 price_first — docs/benchmark/01 line 26).
 * 스키마 정본은 V1__init.sql deal_event; 도메인 방향은 docs/02-domain-model.md.
 * crossVerified는 저장 컬럼이 아니라 sourceSites로부터 파생(m ≥ 2).
 *
 * @param variantId null = 미상(제품 축 판별 불가)
 * @param productCandidates 미상 딜의 제품 후보군(잠정 병합 겹침 판정 — BM-04 AC-3)
 * @param sourceSites 교차검증 근거 사이트 집합(m = size)
 * @param permanentlyExcluded 사기 기각 딜 — 재수집돼도 표본 복귀 없음(BM-05 AC-3)
 */
public record DealEvent(
		Long variantId,
		boolean unclassified,
		Set<Long> productCandidates,
		long priceFirst,
		long priceMin,
		long priceMax,
		long priceLast,
		Origin origin,
		Set<String> sourceSites,
		OutlierFlag outlierFlag,
		boolean permanentlyExcluded,
		DealStatus status,
		Instant firstSeen,
		Instant lastSeen,
		String site,
		String sourceUrl) {

	public DealEvent {
		productCandidates = Set.copyOf(productCandidates);
		sourceSites = Set.copyOf(sourceSites);
	}

	/** 교차검증 성립 = 서로 다른 사이트 2곳 이상. */
	public boolean crossVerified() {
		return sourceSites.size() >= 2;
	}

	/**
	 * docs/03 3-2 lastEvidenceAt — "살아있음의 적극 증거" = max(최신 병합 firstSeen, 마지막 PRICE_CHANGED).
	 * 병합(lastSeen=max)·가격변화만 반영, 단순 생존 재확인은 미반영. lastSeen 컬럼이 이 의미를 담는다.
	 */
	public Instant lastEvidenceAt() {
		return lastSeen;
	}

	/** NEW→ACTIVE(수집 진입, 첫 알림 지점). 비허용 시 거부. */
	public DealEvent activate() {
		return withStatus(status.transitionTo(DealStatus.ACTIVE));
	}

	/** ACTIVE→VERIFIED(2번째 사이트 교차검증). 비허용 시 거부. */
	public DealEvent verify() {
		return withStatus(status.transitionTo(DealStatus.VERIFIED));
	}

	/** ACTIVE·VERIFIED→ENDED(품절·삭제·종료). 비허용 시 거부. */
	public DealEvent end() {
		return withStatus(status.transitionTo(DealStatus.ENDED));
	}

	/**
	 * PRICE_CHANGED — 본문 가격 변화(상태 아님, 이벤트). 대표가(priceFirst)·발생시각(firstSeen)은 불변,
	 * 극값·최근가 갱신 + lastEvidenceAt(적극 증거) 전진(at ≥ 기존일 때). docs/03 3-2.
	 */
	public DealEvent recordPriceChange(long newPrice, Instant at) {
		if (status == DealStatus.ENDED) {
			throw new IllegalStateException("cannot record price change on ENDED deal");
		}
		Instant evidence = at.isAfter(lastSeen) ? at : lastSeen;
		return new DealEvent(variantId, unclassified, productCandidates, priceFirst,
				Math.min(priceMin, newPrice), Math.max(priceMax, newPrice), newPrice,
				origin, sourceSites, outlierFlag, permanentlyExcluded, status, firstSeen, evidence, site, sourceUrl);
	}

	/** 이상치 판정 결과 플래그 부여(BM-05). */
	public DealEvent flagOutlier(OutlierFlag flag) {
		return new DealEvent(variantId, unclassified, productCandidates, priceFirst, priceMin, priceMax, priceLast,
				origin, sourceSites, flag, permanentlyExcluded, status, firstSeen, lastSeen, site, sourceUrl);
	}

	/** 사람이 "진짜였다" 확정 → 이상치 해제, 표본 복귀(BM-05 AC-3). */
	public DealEvent promoteFromOutlier() {
		return flagOutlier(OutlierFlag.NONE);
	}

	/** 사람이 "사기·낚시" 기각 → 영구 제외(재수집돼도 표본 복귀 없음, BM-05 AC-3). */
	public DealEvent reject() {
		return new DealEvent(variantId, unclassified, productCandidates, priceFirst, priceMin, priceMax, priceLast,
				origin, sourceSites, outlierFlag, true, status, firstSeen, lastSeen, site, sourceUrl);
	}

	private DealEvent withStatus(DealStatus newStatus) {
		return new DealEvent(variantId, unclassified, productCandidates, priceFirst, priceMin, priceMax, priceLast,
				origin, sourceSites, outlierFlag, permanentlyExcluded, newStatus, firstSeen, lastSeen, site, sourceUrl);
	}
}
