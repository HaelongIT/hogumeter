package dev.hogumeter.core.domain.alert;

/**
 * AL-04 방해금지 시간 판정(시 0~23). 시작 시각 포함, 끝 시각 제외. 자정 넘김(wrap) 지원.
 * 시작·끝 미설정이거나 같으면 방해금지 없음.
 */
public final class QuietHours {

	private QuietHours() {
	}

	public static boolean isQuiet(int hour, Integer start, Integer end) {
		if (start == null || end == null || start.equals(end)) {
			return false;
		}
		if (start < end) {
			return hour >= start && hour < end;
		}
		return hour >= start || hour < end; // 자정 넘김(예: 23~8)
	}
}
