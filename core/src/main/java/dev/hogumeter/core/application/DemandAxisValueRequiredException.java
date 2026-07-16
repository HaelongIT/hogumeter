package dev.hogumeter.core.application;

/**
 * 분리(SPLIT) 제품인데 어느 수요축 값을 볼지 지정하지 않았다(Q-66 ①).
 *
 * <p><b>왜 하나를 골라 주지 않고 거절하는가</b>: 분리는 "값마다 분포가 다르다"는 사용자의 선언이다.
 * 값을 안 주었는데 전체 딜로 기준가를 내면 그게 곧 <b>묶음</b>이고, 사용자는 분리된 값을 보는 줄 안다 —
 * 화면상 구분이 없으므로 그 거짓말은 조용하다(절대 원칙 1·6). 기본값(첫 번째 색)을 고르는 것도 같은 거짓말이다.
 */
public class DemandAxisValueRequiredException extends RuntimeException {

	public static final String CODE = "BM_DEMAND_AXIS_VALUE_REQUIRED";

	public DemandAxisValueRequiredException(String axisName) {
		super("수요축 분리 제품입니다 — 어느 '" + axisName + "' 값을 볼지 지정하세요");
	}
}
