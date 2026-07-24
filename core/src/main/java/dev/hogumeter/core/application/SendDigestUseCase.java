package dev.hogumeter.core.application;

import dev.hogumeter.core.application.port.out.DigestSender;
import dev.hogumeter.core.domain.digest.DigestSplitter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DIGEST 발송(docs/18) — 렌더링된 본문을 {@link DigestSplitter}로 나눠(DIG-02 절단 금지·연번)
 * {@link DigestSender}로 순서대로 보낸다.
 *
 * <p><b>이번 판의 한계</b>(docs/91 Q-81): 발송 성공 후 저장물({@code digest_state}) 갱신을 아직
 * 하지 않는다 — DIG-02가 요구하는 색·문맥(PUR-05 {@code ObservationContext.mode})·basis 모드
 * 3성분을 variant마다 모으는 배선이 별도 증분이다(관찰 문맥은 Purchase에 걸려 있어 variant→Purchase
 * 조회가 하나 더 필요하다). quiet hours 게이트도 아직 없다 — 다이제스트는 variant별 정책이 아니라
 * 시스템 전역 개념인데 전역 quiet hours 설정이 아직 없어(스케줄 자체가 일요일 20시 KST 고정이라
 * 당장의 위험은 낮다) 배선하지 않았다.
 */
@Service
public class SendDigestUseCase {

	/** 텔레그램 sendMessage 본문 상한(공개 문서상 4096). */
	static final int TELEGRAM_MAX_LENGTH = 4096;

	private final RenderDigestUseCase render;
	private final DigestSender sender;
	private final int maxLength;

	@Autowired
	public SendDigestUseCase(RenderDigestUseCase render, DigestSender sender) {
		this(render, sender, TELEGRAM_MAX_LENGTH);
	}

	/** 테스트 seam — 작은 한도로 분할·연번을 실제로 유발해 검증한다. */
	SendDigestUseCase(RenderDigestUseCase render, DigestSender sender, int maxLength) {
		this.render = render;
		this.sender = sender;
		this.maxLength = maxLength;
	}

	public DigestSendReport send() {
		List<String> parts = DigestSplitter.split(render.render(), maxLength);
		int sent = 0;
		for (String part : parts) {
			if (sender.sendDigest(part)) {
				sent++;
			}
		}
		return new DigestSendReport(parts.size(), sent, sent == parts.size());
	}

	/**
	 * @param parts 분할된 조각 수(안 나뉘면 1)
	 * @param sent 실제로 나간 조각 수
	 * @param allSucceeded 전 분할 성공 — DIG-02 저장물 갱신 조건(아직 저장물 갱신 자체는 안 함, 클래스 javadoc)
	 */
	public record DigestSendReport(int parts, int sent, boolean allSucceeded) {
	}
}
