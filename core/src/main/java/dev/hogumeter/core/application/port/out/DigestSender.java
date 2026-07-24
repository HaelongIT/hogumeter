package dev.hogumeter.core.application.port.out;

/**
 * DIGEST 발송 아웃 포트(docs/18). {@link AlertSender}·{@link UsedAlertSender}와 같은 전송체가 함께
 * 구현한다(봇 토큰·chat_id가 하나라 빈을 나누면 설정이 갈린다) — 포트를 나누는 이유는 메시지 부류가
 * 다르기 때문이다. 이미 완성된 문구({@link dev.hogumeter.core.adapter.telegram.DigestFormatter}의
 * 출력)를 그대로 보낸다 — 다른 포트처럼 도메인 DTO를 넘기고 어댑터가 포맷하지 않는다(다이제스트는
 * 조립 단계에서 이미 문구가 완성된다).
 *
 * <p><b>왜 성공 여부를 돌려주는가</b>(다른 발송 포트와의 차이): DIG-02 "분할 발송…전 분할 성공 시
 * 갱신"이 요구하는 원자성 때문이다. 저장물({@code digest_state}) 갱신 여부를 결정하려면 호출자가
 * <b>모든 조각이 실제로 나갔는지</b> 알아야 한다 — {@link AlertSender#send}처럼 던지지 않고 삼키기만
 * 하면 그 판단을 할 수 없다.
 */
public interface DigestSender {

	boolean sendDigest(String text);
}
