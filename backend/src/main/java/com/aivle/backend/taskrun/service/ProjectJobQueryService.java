package com.aivle.backend.taskrun.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.api.ProjectJobView;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectJobQueryService {
    private static final int MAX_RESULTS = 20;
    private static final List<TaskRunState> ACTIVE_STATES = List.of(
        TaskRunState.QUEUED, TaskRunState.READY, TaskRunState.RUNNING, TaskRunState.NEEDS_INPUT);
    private static final List<TaskRunState> RECENT_STATES = List.of(
        TaskRunState.SUCCEEDED, TaskRunState.NEEDS_INPUT, TaskRunState.FAILED,
        TaskRunState.CANCELLED, TaskRunState.TIMED_OUT);

    private final ProjectRepository projects;
    private final TaskRunRepository taskRuns;

    public ProjectJobQueryService(ProjectRepository projects, TaskRunRepository taskRuns) {
        this.projects = projects;
        this.taskRuns = taskRuns;
    }

    public List<ProjectJobView> active(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return find(projectId, ACTIVE_STATES);
    }

    public List<ProjectJobView> recent(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return find(projectId, RECENT_STATES);
    }

    private List<ProjectJobView> find(Long projectId, List<TaskRunState> states) {
        return taskRuns.findByProjectIdAndStateInAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            projectId, states, PageRequest.of(0, MAX_RESULTS)).stream().map(this::view).toList();
    }

    private void requireOwned(Long ownerId, Long projectId) {
        if (projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId).isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private ProjectJobView view(TaskRun run) {
        JobModule module = module(run.getTaskType());
        String stateKey = run.getState().name().toLowerCase();
        return new ProjectJobView(
            run.getId(), run.getId(), run.getTaskType().name(), run.getSubjectType(), run.getSubjectId(),
            run.getState().name(), "job.title." + module.name().toLowerCase(),
            "job.status." + stateKey, module.name(), run.getStartedAt(), run.getUpdatedAt(),
            run.terminal(), run.isRetryable(), module.route
        );
    }

    private JobModule module(TaskType type) {
        return switch (type) {
            case IDEA_ATTACHMENT_PARSE, IDEA_BRIEF_DERIVATION -> JobModule.IDEA;
            case CONCEPT_FACTORY_RUN, CONCEPT_CANDIDATE, CONCEPT_LEGAL_REVIEW, CONCEPT_REDESIGN -> JobModule.CONCEPT_FACTORY;
            case MARKETING_CONTENT_GENERATION -> JobModule.MARKETING;
        };
    }

    private enum JobModule {
        IDEA("/idea"), CONCEPT_FACTORY("/concepts"), MARKETING("/marketing");
        private final String route;
        JobModule(String route) { this.route = route; }
    }
}
