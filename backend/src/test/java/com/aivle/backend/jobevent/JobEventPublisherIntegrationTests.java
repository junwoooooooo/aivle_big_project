package com.aivle.backend.jobevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JobEventPublisherIntegrationTests {
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired JobEventPublisher publisher;
    @Autowired JobEventQueryService queries;
    @Autowired JobEventRepository events;
    @Autowired JobEventStreamService streams;

    @Test
    void atomicallyAllocatesOrderedSequencesAndReplaysAfterCursor() throws Exception {
        User owner = users.saveAndFlush(User.create("g2-publisher@example.com", "hash", "g2-publisher"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 publisher", null, "AI"));
        String jobId = "g2-atomic-sequence";
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<JobEventView>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int eventIndex = index;
                tasks.add(() -> publisher.publish(command(project.getId(), jobId, eventIndex)));
            }
            List<Future<JobEventView>> results = executor.invokeAll(tasks);
            for (Future<JobEventView> result : results) result.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, project.getId(), 0)).extracting(JobEvent::getSequence)
            .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(queries.replay(owner.getId(), jobId, 5)).extracting(JobEventView::sequence)
            .containsExactly(6L, 7L, 8L);
    }

    @Test
    void rejectsSensitiveParamsAndKeepsTechnicalCodeSeparate() {
        User owner = users.saveAndFlush(User.create("g2-safe@example.com", "hash", "g2-safe"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 safe", null, "AI"));

        JobEventView event = publisher.publish(new JobEventPublisher.Command(
            project.getId(), "g2-safe-job", null, "IDEA_INTAKE", "FILE_EXTRACTION_STARTED",
            JobEvent.Status.RUNNING, "job.idea.file.extraction.started",
            Map.of("fileCount", 2, "format", "DOCX"), "FILE_PARSE_RETRYABLE"));

        assertThat(event.messageParams().get("fileCount").asInt()).isEqualTo(2);
        assertThat(event.technicalCode()).isEqualTo("FILE_PARSE_RETRYABLE");
        assertThatThrownBy(() -> publisher.publish(new JobEventPublisher.Command(
            project.getId(), "g2-unsafe-job", null, "IDEA_INTAKE", "UNSAFE",
            JobEvent.Status.FAILED, "job.idea.failed",
            Map.of("rawProviderBody", "must never be stored"), "PROVIDER_FAILED")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensitive");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "authorization", "rawMessage", "rawPrompt", "requestBody", "providerBody", "fullUserText"
    })
    void rejectsEveryExplicitlyForbiddenPayloadField(String forbiddenField) {
        User owner = users.saveAndFlush(User.create(
            "g2-forbidden-" + forbiddenField.toLowerCase() + "@example.com", "hash", "g2-forbidden"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 forbidden", null, "AI"));

        assertThatThrownBy(() -> publisher.publish(new JobEventPublisher.Command(
            project.getId(), "g2-forbidden-" + forbiddenField.toLowerCase(), null,
            "IDEA_INTAKE", "UNSAFE", JobEvent.Status.FAILED, "job.idea.failed",
            Map.of(forbiddenField, "must never be stored"), "PROVIDER_FAILED")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensitive");
    }

    @Test
    void heartbeatIsTransportOnlyAndNeverPersistsAnotherEvent() {
        User owner = users.saveAndFlush(User.create("g2-heartbeat@example.com", "hash", "g2-heartbeat"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 heartbeat", null, "AI"));
        String jobId = "g2-heartbeat-job";
        publisher.publish(command(project.getId(), jobId, 1));
        long storedBefore = events.count();
        streams.subscribe(jobId, () -> queries.replay(owner.getId(), jobId, 0));

        streams.heartbeat();

        assertThat(events.count()).isEqualTo(storedBefore);
        streams.shutdown();
    }

    @Test
    void rejectsEveryEventAfterNeedsInputTerminalHistory() {
        User owner = users.saveAndFlush(User.create("g2-terminal@example.com", "hash", "g2-terminal"));
        Project project = projects.saveAndFlush(Project.create(owner, "g2 terminal", null, "AI"));
        String jobId = "g2-terminal-job";
        publisher.publish(new JobEventPublisher.Command(project.getId(), jobId, null,
            "NEEDS_INPUT", "job.idea.completed", JobEvent.Status.NEEDS_INPUT,
            "job.idea.completed", Map.of(), null));

        assertThatThrownBy(() -> publisher.publish(new JobEventPublisher.Command(project.getId(), jobId, null,
            "QUEUED", "job.idea.queued", JobEvent.Status.QUEUED,
            "job.idea.queued", Map.of(), null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("TERMINAL_JOB_EVENT_IMMUTABLE");
        assertThat(events.findByJobIdAndProjectIdAndSequenceGreaterThanAndDeletedAtIsNullOrderBySequence(
            jobId, project.getId(), 0)).hasSize(1);
    }

    private JobEventPublisher.Command command(Long projectId, String jobId, int index) {
        return new JobEventPublisher.Command(
            projectId, jobId, null, "REGULATORY_BOUNDARY", "STEP_" + index,
            JobEvent.Status.RUNNING, "job.boundary.step", Map.of("step", index), null);
    }
}
