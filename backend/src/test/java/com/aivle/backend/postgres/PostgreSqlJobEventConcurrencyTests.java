package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.jobevent.JobEventRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
class PostgreSqlJobEventConcurrencyTests extends PostgreSqlIntegrationTestSupport {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired JobEventPublisher publisher;
    @Autowired JobEventRepository events;

    @Test
    void concurrentPublishersPersistEverySequenceExactlyOnce() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        User owner = users.saveAndFlush(User.create(
            "job-events-" + suffix + "@example.com", "hash", "job-event-owner"));
        Project project = projects.saveAndFlush(Project.create(owner, "job event race", null, "AI"));
        String jobId = "postgres-job-events-" + suffix;
        int publisherCount = 12;
        CountDownLatch ready = new CountDownLatch(publisherCount);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(publisherCount);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < publisherCount; index++) {
                int eventIndex = index;
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    publisher.publish(new JobEventPublisher.Command(
                        project.getId(), jobId, null, "IDEA_INTAKE", "STEP_" + eventIndex,
                        JobEvent.Status.RUNNING, "job.idea.status", Map.of("step", eventIndex), null));
                    return null;
                });
            }
            List<Future<Void>> results = tasks.stream().map(pool::submit).toList();
            ready.await();
            start.countDown();
            for (Future<Void> result : results) result.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, project.getId(), 0))
            .hasSize(publisherCount)
            .extracting(JobEvent::getSequence)
            .containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, publisherCount).boxed().toList());
    }
}
