package com.aivle.backend.taskrun.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class E2eTaskRunServiceTests {
    private final TaskRunService taskRuns = mock(TaskRunService.class);
    private final TaskResultRepository results = mock(TaskResultRepository.class);
    private final ProjectEvidenceArtifactService artifacts = mock(ProjectEvidenceArtifactService.class);
    private final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final RestClient aiServer = mock(RestClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private E2eTaskRunService service;

    @BeforeEach
    void setUp() {
        service = new E2eTaskRunService(taskRuns, results, artifacts, hasher, mapper,
            scheduler, aiServer);
        when(hasher.hash(eq(TaskType.CONCEPT_CANDIDATE), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn("sha256:" + "a".repeat(64));
    }

    @Test
    void newCommandUsesCurrentTaskRunAuthorityAndSchedulesAsynchronously() {
        TaskRun run = taskRun("new-key");
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.CONCEPT_CANDIDATE),
            eq("E2E_PIPELINE"), eq("CURRENT_TASK_RUN"), anyString(), anyString(), eq("new-key"),
            eq("correlation"), anyInt()))
            .thenReturn(new TaskRunService.CreateResult(run, true, false));

        var started = service.start(3L, 7L, E2eTaskRunService.Scenario.NORMAL,
            "new-key", "correlation");

        assertThat(started.taskRunId()).isEqualTo(run.getId());
        assertThat(started.createdNew()).isTrue();
        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void idempotentReplayDoesNotScheduleDuplicateExecution() {
        TaskRun run = taskRun("same-key");
        when(taskRuns.createWithDisposition(anyLong(), anyLong(), eq(TaskType.CONCEPT_CANDIDATE),
            eq("E2E_PIPELINE"), eq("CURRENT_TASK_RUN"), anyString(), anyString(), eq("same-key"),
            eq("correlation"), anyInt()))
            .thenReturn(new TaskRunService.CreateResult(run, false, true));

        var replay = service.start(3L, 7L, E2eTaskRunService.Scenario.NORMAL,
            "same-key", "correlation");

        assertThat(replay.taskRunId()).isEqualTo(run.getId());
        assertThat(replay.replayed()).isTrue();
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    private TaskRun taskRun(String key) {
        return TaskRun.create(null, TaskType.CONCEPT_CANDIDATE, "E2E_PIPELINE",
            "CURRENT_TASK_RUN", "{}", "sha256:" + "a".repeat(64), key,
            "correlation", 1);
    }
}
