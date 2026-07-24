package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * DIG-02 저장물 쓰기(docs/18) — "발송 성공 후에만 갱신"(REL-03 원자성)의 쓰는 쪽.
 * {@link ComputeDigestWindowUseCase}가 읽는 {@code digest_state}에 그동안 production writer가
 * 없었다(테스트만 {@code save}했다) — 이 유스케이스가 그 첫 writer다.
 *
 * <p>색·관찰 문맥·basis 모드를 <b>스스로 계산하지 않고 파라미터로 받는다</b> — 그 도출(어느 구매를
 * 볼지, 축 모드를 어떻게 요약할지)은 섹션 조립(DIG-04)의 관심사라 아직 없다(docs/91 Q-81). 이
 * 유스케이스는 "이미 정해진 값을 원자적으로 기록한다"는 좁은 계약만 진다 — {@code AlertSender}가
 * 완성된 메시지를 받아 보내기만 하는 것과 같은 분리다.
 *
 * <p>{@code variant_id}는 수동 할당 {@code @Id}라 {@link DigestStateRepository#save}가 이미
 * 존재하는 행이면 merge(UPDATE), 없으면 insert를 한다 — 존재 확인을 따로 안 해도 된다.
 */
@Service
public class RecordDigestSentUseCase {

	private final DigestStateRepository digestStates;
	private final Clock clock;

	public RecordDigestSentUseCase(DigestStateRepository digestStates, Clock clock) {
		this.digestStates = digestStates;
		this.clock = clock;
	}

	public void recordSent(long variantId, String color, String context, String basisMode) {
		digestStates.save(new DigestStateEntity(variantId, clock.instant(), color, context, basisMode));
	}
}
