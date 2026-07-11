package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * raw_deal_post.status(문자열) → 초기 DealStatus 매핑의 정본(Q-27③). 최초 수집 시 이미 품절/삭제인
 * 원문은 ENDED로 태어나야 알림 경로가 자연히 억제한다. 종료 문자열 집합은 여기 한 곳에만 둔다.
 */
class DealStatusTest {

	@Test
	void soldOutOrDeletedRawStatusBornEnded() {
		assertThat(DealStatus.fromRawPostStatus("SOLD_OUT")).isEqualTo(DealStatus.ENDED);
		assertThat(DealStatus.fromRawPostStatus("DELETED")).isEqualTo(DealStatus.ENDED);
	}

	@Test
	void activeOrUnknownOrNullRawStatusBornActive() {
		assertThat(DealStatus.fromRawPostStatus("ACTIVE")).isEqualTo(DealStatus.ACTIVE);
		assertThat(DealStatus.fromRawPostStatus(null)).isEqualTo(DealStatus.ACTIVE);
		assertThat(DealStatus.fromRawPostStatus("무엇이든")).isEqualTo(DealStatus.ACTIVE);
	}
}
