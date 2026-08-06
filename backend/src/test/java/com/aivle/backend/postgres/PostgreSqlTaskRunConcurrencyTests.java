package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskAttemptRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("postgres")
@SpringBootTest
@ActiveProfiles("test")
class PostgreSqlTaskRunConcurrencyTests extends PostgreSqlIntegrationTestSupport {
    @Autowired TaskRunService service;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired TaskAttemptRepository attempts;
    @Autowired CanonicalInputHasher hasher;

    @Test
    void twoWorkersCreateExactlyOneFirstAttempt() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create("claim-" + suffix + "@example.com", "hash", "owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "claim race", null, null));
        String input = "{}";
        String hash = hasher.hash(TaskType.IDEA_INTERPRETATION, "1.0", "ko-KR", input);
        TaskRun run = service.create(owner.getId(), project.getId(), TaskType.IDEA_INTERPRETATION,
            "IDEA_INTERPRETATION_RUN", "claim-race-" + suffix, input, hash, "create-" + suffix, "correlation-" + suffix, 3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<TaskRunService.Claim> first = pool.submit(() -> claim("worker-1", ready, start));
            Future<TaskRunService.Claim> second = pool.submit(() -> claim("worker-2", ready, start));
            ready.await();
            start.countDown();
            List<TaskRunService.Claim> claims =
                java.util.stream.Stream.of(first.get(), second.get())
                    .filter(java.util.Objects::nonNull)
                    .toList();

            assertThat(claims).singleElement();
            assertThat(attempts.findAll()).singleElement().satisfies(attempt -> {
                assertThat(attempt.getTaskRun().getId()).isEqualTo(run.getId());
                assertThat(attempt.getAttemptNumber()).isEqualTo(1);
            });
        } finally {
            pool.shutdownNow();
        }
    }

    private TaskRunService.Claim claim(String worker, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.claimNext(worker, Duration.ofSeconds(30), Duration.ofMinutes(2));
    }
}
