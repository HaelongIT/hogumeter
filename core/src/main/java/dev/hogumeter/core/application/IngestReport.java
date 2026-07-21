package dev.hogumeter.core.application;

/**
 * 한 번의 {@link IngestDealsUseCase#ingestPending()}이 무엇을 했는가(OBS-02 매칭 카운터, Q-57 ②③).
 * 스냅샷 차이로는 셀 수 없는 값들이다 — 매칭 tier는 링크·딜 수에 흔적을 남기지 않고(CANDIDATE·REJECTED는
 * 딜을 만들지 않는다), 가격 없음 스킵도 마찬가지다. 그래서 유스케이스가 직접 세어 반환한다.
 *
 * <p>부류가 다른 사실을 한 카운터로 합치지 않는다: {@code candidate}(사람이 볼 후보)와 {@code unknown}
 * (매칭 실패)은 둘 다 리뷰 큐로 가지만 성질이 다르고, {@code rejected}(무관)는 큐에도 안 간다.
 * {@code firstAlertsSent}는 이번 수집에서 실제로 나간 <b>첫 알림</b> 수다(후속 알림은 별개 단계·별개 카운터).
 *
 * <p>{@code heldAlerts}는 방해금지(quiet hours)로 <b>보류된</b> 첫 알림 수다 — 발송과 부류가 다르다.
 * ⚠️ 지금은 보류분을 담을 큐도, 종료 시 플러시도 <b>없다</b>(Q-20 ②) — 즉 이 알림들은 <b>유실</b>된다.
 * 그 손실을 카운터로 <b>보이게</b> 한다("성공했는데 조용히 0건"의 반대: 조용히 유실되면 못 고친다).
 * 이 값이 매 틱 0이 아니면 플러시 미구현이 사람 눈에 든다.
 */
public record IngestReport(int confirmed, int candidate, int unknown, int rejected,
		int skippedNoPrice, int firstAlertsSent, int heldAlerts) {

	public static IngestReport empty() {
		return new IngestReport(0, 0, 0, 0, 0, 0, 0);
	}
}
