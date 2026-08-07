package dev.hogumeter.core.adapter.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}를 켠다.
 *
 * <p>{@code CoreApplication}(기존 파일)에 애노테이션을 얹지 않고 <b>새 설정 클래스</b>에 둔다 —
 * 관심사를 분리해 애플리케이션 진입점을 가볍게 유지한다. 컴포넌트 스캔이 잡아간다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
