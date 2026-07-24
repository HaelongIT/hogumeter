package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.domain.digest.DigestRules;
import dev.hogumeter.core.domain.signal.SignalColor;
import org.springframework.stereotype.Service;

/**
 * DIG-04 ② 전환(docs/18) — 저장된 신호 색 vs 현재 신호 색 양끝 비교. {@link DigestRules#isReportableTransition}
 * (지금까지 호출자 0이던 순수 규칙)의 첫 소비자다.
 *
 * <p><b>색만 본다</b>(스펙 그대로): 색이 같으면 관찰 문맥·basis 모드(억제 신호 2종)가 바뀌어도 전환으로
 * 보고하지 않는다. 그 두 신호는 색을 바꾸지 못하므로 {@code digest_state}에 저장은 하되(전환 계산엔
 * 쓰지 않는다) — 억제의 정의가 곧 "색이 안 바뀌면 억제"다.
 *
 * <p><b>현재 색</b>은 {@link GetSignalUseCase}가 낸다 — 판단 화면이 보여주는 바로 그 색이라, 다이제스트가
 * 다른 계산으로 다른 색을 말하는 일이 없다(SPLIT도 판단 화면과 같이 variant 전체로 집계, 섹션 ③과 동일).
 *
 * <p><b>첫 다이제스트</b>(발송 이력 없음 → 저장 색 없음)는 전환이 아니다 — 비교할 이전 값이 없으므로
 * 기준선일 뿐이다({@code from=null, reportable=false}).
 */
@Service
public class ComputeDigestTransitionUseCase {

	private final DigestStateRepository digestStates;
	private final GetSignalUseCase signals;

	public ComputeDigestTransitionUseCase(DigestStateRepository digestStates, GetSignalUseCase signals) {
		this.digestStates = digestStates;
		this.signals = signals;
	}

	public DigestTransition transition(long variantId) {
		SignalColor current = signals.getSignal(variantId).color();
		SignalColor stored = digestStates.findById(variantId)
				.map(DigestStateEntity::getStoredColor)
				.map(ComputeDigestTransitionUseCase::parse)
				.orElse(null);
		boolean reportable = stored != null && DigestRules.isReportableTransition(stored, current);
		return new DigestTransition(stored, current, reportable);
	}

	/**
	 * 저장된 색 문자열 → enum. 우리가 쓴 값이라 정상 경로는 항상 유효하지만, 손상된 값(수동 DB 수정 등)을
	 * 만나면 "저장 색 없음"과 같이 취급한다 — 예외로 다이제스트 전체를 막느니 그 variant를 기준선으로 되돌린다.
	 */
	private static SignalColor parse(String stored) {
		try {
			return SignalColor.valueOf(stored);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	/**
	 * @param from 이전(저장) 색 — 첫 다이제스트면 null
	 * @param to 현재 색
	 * @param reportable 색이 바뀌었고 이전 값이 있어 보고 대상인가
	 */
	public record DigestTransition(SignalColor from, SignalColor to, boolean reportable) {
	}
}
