package dev.hogumeter.core.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;

/**
 * "애노테이션이 붙어 있다"와 "스케줄이 등록됐다"는 다른 사건이다. {@code @EnableScheduling}이 빠지면
 * {@code @Scheduled}는 조용히 무시된다 — 아무 에러도 없이 파이프라인이 영원히 안 돈다.
 *
 * <p>{@code sleep}으로 실행을 기다리지 않는다(docs/21: 테스트에 sleep 금지). 등록된 태스크 목록을 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "core.pipeline.enabled=true")
class PipelineSchedulerWiringTest {

	@Autowired
	private Collection<ScheduledTaskHolder> taskHolders;

	@Autowired
	private PipelineScheduler scheduler;

	@Test
	@DisplayName("PipelineScheduler.tick이 실제로 스케줄에 등록된다 (@EnableScheduling이 살아 있다)")
	void tickIsRegisteredAsAScheduledTask() {
		assertThat(scheduler).isNotNull();

		assertThat(taskHolders)
				.as("@EnableScheduling이 없으면 ScheduledTaskHolder 자체가 없다")
				.isNotEmpty();

		assertThat(taskHolders.stream()
				.map(ScheduledTaskHolder::getScheduledTasks)
				.flatMap(Collection::stream)
				.map(ScheduledTask::getTask)
				.map(Task::toString))
				.as("등록된 스케줄 태스크 중에 PipelineScheduler.tick이 있어야 한다")
				.anyMatch(task -> task.contains("PipelineScheduler") && task.contains("tick"));
	}
}
