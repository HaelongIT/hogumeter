package dev.hogumeter.core.adapter.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled}를 켠다.
 *
 * <p>{@code CoreApplication}(기존 파일)에 애노테이션을 얹지 않고 <b>새 설정 클래스</b>에 둔다 —
 * core는 상대 개발자 영역이라 기존 파일 수정 없이 additive로만 들어간다. 컴포넌트 스캔이 잡아간다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
