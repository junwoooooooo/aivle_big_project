package com.aivle.backend.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ProjectJobQueryServiceTests {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final TaskRunRepository taskRuns = mock(TaskRunRepository.class);
    private final ProjectJobQueryService service = new ProjectJobQueryService(projects, taskRuns);

    @Test
    void ownerCanRestoreActiveJobsFromTaskRunTruth() {
        TaskRun run = mock(TaskRun.class);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 2L)).thenReturn(Optional.of(mock(Project.class)));
        when(taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(eq(9L), any(), any(Pageable.class)))
            .thenReturn(List.of(run));
        when(run.getId()).thenReturn("job-1");
        when(run.getTaskType()).thenReturn(TaskType.CONCEPT_FACTORY_RUN);
        when(run.getSubjectType()).thenReturn("CONCEPT_FACTORY_RUN");
        when(run.getSubjectId()).thenReturn("run-1");
        when(run.getState()).thenReturn(TaskRunState.RUNNING);
        when(run.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 7, 11, 0));

        var jobs = service.active(2L, 9L);

        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.jobId()).isEqualTo("job-1");
            assertThat(job.module()).isEqualTo("CONCEPT_FACTORY");
            assertThat(job.targetRoute()).isEqualTo("/concepts");
            assertThat(job.terminal()).isFalse();
        });
    }

    @Test
    void nonOwnerCannotQueryProjectJobs() {
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(9L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.active(3L, 9L)).isInstanceOf(BusinessException.class);
        verify(projects).findByIdAndOwnerIdAndDeletedAtIsNull(9L, 3L);
    }
}
