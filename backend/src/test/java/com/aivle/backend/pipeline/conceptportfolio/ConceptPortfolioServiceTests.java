package com.aivle.backend.pipeline.conceptportfolio;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.conceptportfolio.api.ConceptPortfolioApiModels.CreateRunRequest;
import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.*;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.*;
import com.aivle.backend.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

class ConceptPortfolioServiceTests {
    ProjectRepository projects = mock(ProjectRepository.class);
    IdeaBriefRepository briefs = mock(IdeaBriefRepository.class);
    IdeaBriefFieldRepository fields = mock(IdeaBriefFieldRepository.class);
    ConceptPortfolioRunRepository runs = mock(ConceptPortfolioRunRepository.class);
    ConceptPortfolioConceptRepository concepts = mock(ConceptPortfolioConceptRepository.class);
    ConceptInputRequestRepository inputs = mock(ConceptInputRequestRepository.class);
    ConceptPortfolioSeedBuilder seeds = mock(ConceptPortfolioSeedBuilder.class);
    CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
    TaskRunService taskRuns = mock(TaskRunService.class);
    JobEventPublisher events = mock(JobEventPublisher.class);
    ObjectMapper mapper = new ObjectMapper();
    Project project = mock(Project.class);
    User owner = mock(User.class);
    IdeaBrief brief = mock(IdeaBrief.class);
    TaskRun task = mock(TaskRun.class);
    ConceptPortfolioService service;

    @BeforeEach
    void setUp() {
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(project.getId()).thenReturn(42L);
        when(brief.getId()).thenReturn("brief"); when(brief.getProject()).thenReturn(project);
        when(brief.isConfirmed()).thenReturn(true); when(brief.getSnapshotHash()).thenReturn("sha256:" + "b".repeat(64));
        when(brief.getConfirmedSnapshotId()).thenReturn("brief");
        when(projects.findByIdForUpdate(42L)).thenReturn(Optional.of(project));
        when(briefs.findByIdAndProjectIdAndDeletedAtIsNull("brief", 42L)).thenReturn(Optional.of(brief));
        when(briefs.findCurrentOwned(7L, 42L)).thenReturn(Optional.of(brief));
        when(fields.findAllByBriefIdOrderById("brief")).thenReturn(List.of());
        when(seeds.build(brief, List.of(), 5)).thenReturn(new ConceptPortfolioSeedBuilder.BuiltInput(
            mapper.readTree("{\"seed\":{},\"maxConcepts\":5}"), "{\"seed\":{},\"maxConcepts\":5}"));
        when(hasher.hash(any(), eq("1.0"), eq("ko-KR"), anyString()))
            .thenReturn("sha256:" + "a".repeat(64));
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(task.getId()).thenReturn("task");
        when(taskRuns.create(anyLong(), anyLong(), any(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyInt())).thenReturn(task);
        service = new ConceptPortfolioService(projects, briefs, fields, runs, concepts, inputs,
            seeds, hasher, taskRuns, events, mapper);
    }

    @Test
    void createsDurableV2TaskAndReplaysSameIdempotencyKey() {
        var created = service.create(7L, 42L, new CreateRunRequest("brief", null, "idem"));
        assertThat(created.productStatus()).isEqualTo(ConceptPortfolioRunStatus.QUEUED);
        assertThat(created.taskRunId()).isEqualTo("task");
        verify(taskRuns).create(eq(7L), eq(42L),
            eq(com.aivle.backend.taskrun.domain.TaskType.CONCEPT_PORTFOLIO_V2_RUN),
            eq("CONCEPT_PORTFOLIO_RUN"), eq(created.runId()), anyString(),
            eq("sha256:" + "a".repeat(64)), eq("idem"), anyString(), eq(2));

        ConceptPortfolioRun replay = mock(ConceptPortfolioRun.class);
        when(replay.getRequestHash()).thenReturn("sha256:" + "a".repeat(64));
        when(replay.getId()).thenReturn(created.runId()); when(replay.getSourceIdeaBrief()).thenReturn(brief);
        when(replay.getSourceSnapshotHash()).thenReturn("sha256:" + "b".repeat(64));
        when(replay.getProductStatus()).thenReturn(ConceptPortfolioRunStatus.QUEUED);
        when(replay.getInitialTaskRunId()).thenReturn("task");
        when(runs.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(42L, "idem"))
            .thenReturn(Optional.of(replay));
        service.create(7L, 42L, new CreateRunRequest("brief", 5, "idem"));
        verify(taskRuns, times(1)).create(anyLong(), anyLong(), any(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void rejectsIdempotencyConflictActiveDuplicateAndOwnershipViolation() {
        ConceptPortfolioRun replay = mock(ConceptPortfolioRun.class);
        when(replay.getRequestHash()).thenReturn("sha256:" + "c".repeat(64));
        when(runs.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(42L, "idem"))
            .thenReturn(Optional.of(replay));
        assertCode(() -> service.create(7L, 42L, new CreateRunRequest("brief", 5, "idem")),
            ErrorCode.IDEMPOTENCY_CONFLICT);

        when(runs.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(42L, "other"))
            .thenReturn(Optional.empty());
        when(runs.findFirstByProjectIdAndProductStatusInAndDeletedAtIsNull(eq(42L), any()))
            .thenReturn(Optional.of(mock(ConceptPortfolioRun.class)));
        assertCode(() -> service.create(7L, 42L, new CreateRunRequest("brief", 5, "other")),
            ErrorCode.ANALYSIS_ALREADY_RUNNING);

        when(projects.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        assertCode(() -> service.create(99L, 42L, new CreateRunRequest("brief", 5, "owner")),
            ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void rejectsNonConfirmedSnapshot() {
        when(brief.isConfirmed()).thenReturn(false);
        assertCode(() -> service.create(7L, 42L, new CreateRunRequest("brief", 5, "idem")),
            ErrorCode.IDEA_NOT_CONFIRMED);
    }

    @ParameterizedTest
    @EnumSource(value = ConceptPortfolioRunStatus.class, names = {
        "FAILED", "NEEDS_INPUT", "RESULTS_AVAILABLE", "RESULTS_WITH_OPEN_INPUT", "STALE"
    })
    void terminalCurrentIsFlushedBeforeFreshQueuedRunIsInserted(ConceptPortfolioRunStatus status) {
        ConceptPortfolioRun previous = mock(ConceptPortfolioRun.class);
        when(previous.getProductStatus()).thenReturn(status);
        when(runs.findCurrentForUpdate(42L)).thenReturn(Optional.of(previous));

        var created = service.create(7L, 42L, new CreateRunRequest("brief", 5, "fresh-key"));

        InOrder order = inOrder(runs);
        order.verify(runs).findCurrentForUpdate(42L);
        order.verify(runs).saveAndFlush(previous);
        order.verify(runs).saveAndFlush(argThat((ConceptPortfolioRun value) -> value != previous
            && value.isCurrent() && value.getProductStatus() == ConceptPortfolioRunStatus.QUEUED));
        verify(previous).markStale();
        assertThat(created.productStatus()).isEqualTo(ConceptPortfolioRunStatus.QUEUED);
    }

    @Test
    void currentConstraintRaceIsNormalizedAsAnalysisAlreadyRunning() {
        when(runs.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_cp_run_current"));
        assertCode(() -> service.create(7L, 42L,
            new CreateRunRequest("brief", 5, "concurrent-key")), ErrorCode.ANALYSIS_ALREADY_RUNNING);
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
    }
}
