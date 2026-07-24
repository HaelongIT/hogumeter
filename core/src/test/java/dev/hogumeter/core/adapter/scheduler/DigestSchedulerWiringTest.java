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
 * {@code PipelineSchedulerWiringTest}와 같은 계약 — "애노테이션이 붙어 있다"와 "스케줄이 등록됐다"는
 * 다른 사건이다. {@code sleep}으로 실행을 기다리지 않고 등록된 태스크 목록을 직접 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "core.digest.enabled=true")
class DigestSchedulerWiringTest {

	@Autowired
	private Collection<ScheduledTaskHolder> taskHolders;

	@Autowired
	private DigestScheduler scheduler;

	@Test
	@DisplayName("DigestScheduler.tick이 실제로 스케줄에 등록된다 (일요일 20시 KST)")
	void tickIsRegisteredAsAScheduledTask() {
		assertThat(scheduler).isNotNull();

		assertThat(taskHolders.stream()
				.map(ScheduledTaskHolder::getScheduledTasks)
				.flatMap(Collection::stream)
				.map(ScheduledTask::getTask)
				.map(Task::toString))
				.as("등록된 스케줄 태스크 중에 DigestScheduler.tick이 있어야 한다")
				.anyMatch(task -> task.contains("DigestScheduler") && task.contains("tick"));
	}
}
