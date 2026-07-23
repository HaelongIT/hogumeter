package dev.hogumeter.core.application;

import java.util.List;

/**
 * 한 번의 {@link IngestDealsUseCase#ingestPending()}이 무엇을 했는가(OBS-02 매칭 카운터, Q-57 ②③).
 * 스냅샷 차이로는 셀 수 없는 값들이다 — 매칭 tier는 링크·딜 수에 흔적을 남기지 않고(CANDIDATE·REJECTED는
 * 딜을 만들지 않는다), 가격 없음 스킵도 마찬가지다. 그래서 유스케이스가 직접 세어 반환한다.
 *
 * <p>부류가 다른 사실을 한 카운터로 합치지 않는다: {@code candidate}(사람이 볼 후보)와 {@code unknown}
 * (매칭 실패)은 둘 다 리뷰 큐로 가지만 성질이 다르고, {@code rejected}(무관)는 큐에도 안 간다.
 * {@code firstAlertsSent}는 이번 수집에서 실제로 나간 <b>첫 알림</b> 수다(후속 알림은 별개 단계·별개 카운터).
 *
 * <p>{@code heldAlerts}는 이번 틱에 방해금지(quiet hours)로 <b>새로 보류된</b> 첫 알림 수다 — 발송과 부류가
 * 다르다. 보류분은 {@code held_alert} 큐에 적혀 방해금지가 끝난 틱에 재평가·발송된다(Q-20 ②,
 * {@code FlushHeldAlertsUseCase}). 그 종료분 처리 결과는 틱 리포트의 {@code heldAlertsFlushed/Dropped}가 낸다.
 *
 * <p>{@code skippedForeignSource}는 <b>신품으로 해석하지 않기로 한 소스</b>의 게시물 수다
 * ({@link dev.hogumeter.core.domain.deal.NewProductSources}). 중고 마켓(번개장터)과 모르는 소스가 여기
 * 들어간다 — {@code rejected}(신품 게시판의 글인데 우리 카탈로그와 무관)와 <b>부류가 다르다.</b> 전자는
 * "우리가 안 읽기로 한 곳"이고 후자는 "읽었는데 내 물건이 아님"이다. 합쳐 세면 아무것도 말하지 않는다.
 * 이 수가 오르면 collector 레지스트리와 core 허용집합이 어긋났다는 신호이기도 하다.
 *
 * <p>{@code mergedDealIds}는 이번 수집에서 <b>두 번째 이상 사이트에 흡수(병합)된</b> 딜의 id다(BM-04).
 * 병합은 첫 알림을 다시 내지 않는다(Q-13) — 딜의 {@code priceFirst}는 병합으로 안 바뀌므로 같은 트리거를
 * 재평가하면 매번 다시 발화할 위험이 있다. 대신 이 id들은 VERIFIED 후속 알림(AL-03) 대상으로만 흐른다
 * ({@code PipelineScheduler}가 {@code FollowUpAlertUseCase}에 종류 VERIFIED로 넘긴다) — 그 발송도
 * "첫 알림이 이미 나간 딜에만·종류당 1회"로 멱등이라 중복 병합에도 안전하다.
 */
public record IngestReport(int confirmed, int candidate, int unknown, int rejected,
		int skippedNoPrice, int firstAlertsSent, int heldAlerts, int skippedForeignSource,
		List<Long> mergedDealIds) {

	public IngestReport {
		mergedDealIds = List.copyOf(mergedDealIds);
	}

	public static IngestReport empty() {
		return new IngestReport(0, 0, 0, 0, 0, 0, 0, 0, List.of());
	}
}
