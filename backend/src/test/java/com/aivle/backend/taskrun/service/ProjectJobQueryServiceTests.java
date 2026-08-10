package com.aivle.backend.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptInputRequestRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ProjectJobQueryServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final IdeaBriefRepository ideaBriefs = mock(IdeaBriefRepository.class);
    private final ConceptInputRequestRepository conceptInputs = mock(ConceptInputRequestRepository.class);
    private final ProjectJobQueryService service = new ProjectJobQueryService(
        projects, taskRuns, ideaBriefs, conceptInputs);

    @Test
    void ownerCanRestoreActiveJobsFromTaskRunTruth() {
        TaskRun run = mock(TaskRun.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(9L);
        when(run.getProject()).thenReturn(project);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(eq(9L), any(), any(Pageable.class)))
            .thenReturn(List.of(run));
        when(run.getId()).thenReturn("job-1");
        when(run.getTaskType()).thenReturn(TaskType.CONCEPT_FACTORY_RUN);
        when(run.getSubjectType()).thenReturn("CONCEPT_FACTORY_RUN");
        when(run.getSubjectId()).thenReturn("run-1");
        when(run.getState()).thenReturn(TaskRunState.RUNNING);
        when(run.getStartedAt()).thenReturn(LocalDateTime.of(2026, 8, 7, 6, 21));
        when(run.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 7, 11, 0));

        var jobs = service.active(2L, 9L);

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.jobId()).isEqualTo("job-1");
            assertThat(job.module()).isEqualTo("CONCEPT_FACTORY");
            assertThat(job.targetRoute()).isEqualTo("/concepts");
            assertThat(job.terminal()).isFalse();
            assertThat(job.startedAt()).isEqualTo(Instant.parse("2026-08-07T06:21:00Z"));
            assertThat(job.updatedAt()).isEqualTo(Instant.parse("2026-08-07T11:00:00Z"));
        });
    }

    @Test
    void failedConceptFactoryTaskIsAbsentFromActiveAndVisibleInRecent() {
        TaskRun run = mock(TaskRun.class);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(9L);
        when(run.getProject()).thenReturn(project);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
                eq(9L), any(), any(Pageable.class))).thenAnswer(invocation -> {
            List<TaskRunState> states = invocation.getArgument(1);
            return states.contains(TaskRunState.FAILED) ? List.of(run) : List.of();
        });
        when(run.getId()).thenReturn("job-failed");
        when(run.getTaskType()).thenReturn(TaskType.CONCEPT_FACTORY_RUN);
        when(run.getSubjectType()).thenReturn("CONCEPT_FACTORY_RUN");
        when(run.getSubjectId()).thenReturn("run-failed");
        when(run.getState()).thenReturn(TaskRunState.FAILED);
        when(run.terminal()).thenReturn(true);

        assertThat(service.active(2L, 9L)).isEmpty();
        assertThat(service.recent(2L, 9L)).singleElement().satisfies(job -> {
            assertThat(job.jobId()).isEqualTo("job-failed");
            assertThat(job.rawStatus()).isEqualTo("FAILED");
            assertThat(job.terminal()).isTrue();
        });
    }

    @Test
    void nonOwnerCannotQueryProjectJobs() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.active(3L, 9L)).isInstanceOf(BusinessException.class);
        verify(projects).findByIdAndOwnerIdAndDeletedAtIsNull(9L, 3L);
    }

    @Test
    void resolvedNeedsInputMovesToRecentWhenANewerJobExists() {
        Project project = mock(Project.class);
        TaskRun oldNeedsInput = ideaRun("job-a", project, TaskRunState.NEEDS_INPUT);
        TaskRun newerRunning = ideaRun("job-b", project, TaskRunState.RUNNING);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(newerRunning, oldNeedsInput));
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            9L, "IDEA_BRIEF", "brief-1")).thenReturn(Optional.of(newerRunning));
        when(ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull("brief-1", 9L))
            .thenReturn(Optional.of(mock(IdeaBrief.class)));

        assertThat(service.active(2L, 9L)).extracting(job -> job.jobId()).containsExactly("job-b");

        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(oldNeedsInput));
        assertThat(service.recent(2L, 9L)).singleElement().satisfies(job -> {
            assertThat(job.rawStatus()).isEqualTo("NEEDS_INPUT");
            assertThat(job.actionable()).isFalse();
            assertThat(job.presentationStatus()).isEqualTo("RESOLVED_INPUT");
        });
    }

    @Test
    void latestNeedsInputStaysActionableWhileDomainStillNeedsInput() {
        Project project = mock(Project.class);
        TaskRun needsInput = ideaRun("job-a", project, TaskRunState.NEEDS_INPUT);
        IdeaBrief brief = mock(IdeaBrief.class);
        when(brief.getStatus()).thenReturn(com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus.NEEDS_INPUT);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(needsInput));
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            9L, "IDEA_BRIEF", "brief-1")).thenReturn(Optional.of(needsInput));
        when(ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull("brief-1", 9L)).thenReturn(Optional.of(brief));

        assertThat(service.active(2L, 9L)).singleElement().satisfies(job -> {
            assertThat(job.actionable()).isTrue();
            assertThat(job.presentationStatus()).isEqualTo("NEEDS_INPUT");
        });
    }

    @Test
    void onlyLatestNeedsInputForTheSubjectIsActionable() {
        Project project = mock(Project.class);
        TaskRun oldNeedsInput = ideaRun("job-a", project, TaskRunState.NEEDS_INPUT);
        TaskRun latestNeedsInput = ideaRun("job-b", project, TaskRunState.NEEDS_INPUT);
        IdeaBrief brief = mock(IdeaBrief.class);
        when(brief.getStatus()).thenReturn(com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus.NEEDS_INPUT);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(latestNeedsInput, oldNeedsInput));
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            9L, "IDEA_BRIEF", "brief-1")).thenReturn(Optional.of(latestNeedsInput));
        when(ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull("brief-1", 9L)).thenReturn(Optional.of(brief));

        assertThat(service.active(2L, 9L)).extracting(job -> job.jobId()).containsExactly("job-b");
    }

    @Test
    void oldPortfolioNeedsInputBecomesNonActionableAfterContinuationJobExists() {
        Project project = mock(Project.class);
        TaskRun initial = portfolioRun("initial", project, TaskType.CONCEPT_PORTFOLIO_V2_RUN,
            TaskRunState.NEEDS_INPUT);
        TaskRun continuation = portfolioRun("continue", project, TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE,
            TaskRunState.RUNNING);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            9L, "CONCEPT_PORTFOLIO_RUN", "portfolio-1")).thenReturn(Optional.of(continuation));
        when(conceptInputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("portfolio-1"), any()))
            .thenReturn(1L);
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(continuation, initial));

        assertThat(service.active(2L, 9L)).extracting(job -> job.jobId()).containsExactly("continue");
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(initial));
        assertThat(service.recent(2L, 9L)).singleElement().satisfies(job -> {
            assertThat(job.actionable()).isFalse();
            assertThat(job.presentationStatus()).isEqualTo("RESOLVED_INPUT");
        });
    }

    @Test
    void latestPortfolioNeedsInputIsActionableOnlyWithOpenDomainRequest() {
        Project project = mock(Project.class);
        TaskRun continuation = portfolioRun("continue", project, TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE,
            TaskRunState.NEEDS_INPUT);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(project));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            eq(9L), any(), any(Pageable.class))).thenReturn(List.of(continuation));
        when(taskRuns.findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            9L, "CONCEPT_PORTFOLIO_RUN", "portfolio-1")).thenReturn(Optional.of(continuation));
        when(conceptInputs.countByRunIdAndStatusInAndDeletedAtIsNull(eq("portfolio-1"), any()))
            .thenReturn(1L);

        assertThat(service.active(2L, 9L)).singleElement()
            .satisfies(job -> assertThat(job.actionable()).isTrue());
    }

    private TaskRun ideaRun(String id, Project project, TaskRunState state) {
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(9L);
        when(run.getTaskType()).thenReturn(TaskType.IDEA_BRIEF_DERIVATION);
        when(run.getSubjectType()).thenReturn("IDEA_BRIEF");
        when(run.getSubjectId()).thenReturn("brief-1");
        when(run.getState()).thenReturn(state);
        when(run.terminal()).thenReturn(state == TaskRunState.NEEDS_INPUT);
        return run;
    }

    private TaskRun portfolioRun(String id, Project project, TaskType type, TaskRunState state) {
        TaskRun run = mock(TaskRun.class);
        when(run.getId()).thenReturn(id); when(run.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(9L); when(run.getTaskType()).thenReturn(type);
        when(run.getSubjectType()).thenReturn("CONCEPT_PORTFOLIO_RUN");
        when(run.getSubjectId()).thenReturn("portfolio-1"); when(run.getState()).thenReturn(state);
        when(run.terminal()).thenReturn(state == TaskRunState.NEEDS_INPUT);
        return run;
    }
}
