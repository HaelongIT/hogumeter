package dev.hogumeter.core.domain.alert;

/**
 * 알림 정책(순수 값) — V1__init.sql alert_policy의 알림 판정 관련 필드 투영.
 * quietHours는 시(0~23) 단위. targetPrice·quietHours 모두 선택(null 허용).
 *
 * @param targetPrice 목표가(이하면 TARGET 트리거), null이면 미설정
 * @param quietHoursStart 방해금지 시작 시(포함), null이면 미설정
 * @param quietHoursEnd 방해금지 끝 시(제외), null이면 미설정
 */
public record AlertPolicy(Long targetPrice, Integer quietHoursStart, Integer quietHoursEnd) {
}
