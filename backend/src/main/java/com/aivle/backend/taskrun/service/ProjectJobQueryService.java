package com.aivle.backend.taskrun.service;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefStatus;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.conceptportfolio.domain.ConceptInputRequestStatus;
import com.aivle.backend.pipeline.conceptportfolio.repository.ConceptInputRequestRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.api.ProjectJobView;
import com.aivle.backend.taskrun.api.ProjectJobHistoryResponse;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.util.List;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final IdeaBriefRepository ideaBriefs;
    private final ConceptInputRequestRepository conceptInputs;

    public ProjectJobQueryService(ProjectRepository projects, TaskRunRepository taskRuns,
            IdeaBriefRepository ideaBriefs, ConceptInputRequestRepository conceptInputs) {
        this.projects = projects;
        this.taskRuns = taskRuns;
        this.ideaBriefs = ideaBriefs;
        this.conceptInputs = conceptInputs;
    }

    public List<ProjectJobView> active(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return find(projectId, ACTIVE_STATES).stream()
            .filter(job -> job.rawStatus().equals(TaskRunState.NEEDS_INPUT.name()) ? job.actionable() : true)
            .toList();
    }

    public List<ProjectJobView> recent(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        return find(projectId, RECENT_STATES).stream()
            .filter(job -> !job.rawStatus().equals(TaskRunState.NEEDS_INPUT.name()) || !job.actionable())
            .toList();
    }

    public ProjectJobHistoryResponse history(Long ownerId, Long projectId, int page, int size) {
        requireOwned(ownerId, projectId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        var result = taskRuns.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(
            projectId, PageRequest.of(safePage, safeSize));
        return new ProjectJobHistoryResponse(result.getContent().stream().map(this::view).toList(),
            result.getNumber(), result.getSize(), result.hasNext(), result.getTotalElements());
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
        JobModule module = module(run);
        String rawStatus = run.getState().name();
        boolean actionable = actionable(run);
        String presentationStatus = presentationStatus(run.getState(), actionable);
        String stateKey = presentationStatus.toLowerCase();
        boolean latestForSubject = taskRuns
            .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                run.getProject().getId(), run.getSubjectType(), run.getSubjectId())
            .map(latest -> latest.getId().equals(run.getId())).orElse(false);
        return new ProjectJobView(
            run.getId(), run.getId(), run.getTaskType().name(), run.getSubjectType(), run.getSubjectId(),
            rawStatus, rawStatus, actionable, presentationStatus,
            "job.title." + module.name().toLowerCase(),
            "job.status." + stateKey, module.name(), utc(run.getStartedAt()), utc(run.getUpdatedAt()), latestForSubject,
            run.terminal(), run.isRetryable(), module.route
        );
    }

    private Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private boolean actionable(TaskRun run) {
        if (run.getState() != TaskRunState.NEEDS_INPUT) {
            return run.getState() == TaskRunState.QUEUED || run.getState() == TaskRunState.READY
                || run.getState() == TaskRunState.RUNNING;
        }
        if ((run.getTaskType() == TaskType.CONCEPT_PORTFOLIO_V2_RUN
                || run.getTaskType() == TaskType.CONCEPT_PORTFOLIO_V2_CONTINUE)
                && "CONCEPT_PORTFOLIO_RUN".equals(run.getSubjectType())) {
            boolean latestForSubject = taskRuns
                .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                    run.getProject().getId(), run.getSubjectType(), run.getSubjectId())
                .map(latest -> latest.getId().equals(run.getId())).orElse(false);
            boolean unresolved = conceptInputs.countByRunIdAndStatusInAndDeletedAtIsNull(
                run.getSubjectId(), List.of(ConceptInputRequestStatus.OPEN)) > 0;
            return latestForSubject && unresolved;
        }
        if (run.getTaskType() != TaskType.IDEA_BRIEF_DERIVATION || !"IDEA_BRIEF".equals(run.getSubjectType())) {
            return true;
        }
        boolean latestForSubject = taskRuns
            .findFirstByProjectIdAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                run.getProject().getId(), run.getSubjectType(), run.getSubjectId())
            .map(latest -> latest.getId().equals(run.getId()))
            .orElse(false);
        boolean domainNeedsInput = ideaBriefs.findByIdAndProjectIdAndDeletedAtIsNull(
                run.getSubjectId(), run.getProject().getId())
            .map(brief -> brief.getStatus() == IdeaBriefStatus.NEEDS_INPUT)
            .orElse(false);
        return latestForSubject && domainNeedsInput;
    }

    private String presentationStatus(TaskRunState rawStatus, boolean actionable) {
        if (rawStatus == TaskRunState.NEEDS_INPUT && !actionable) return "RESOLVED_INPUT";
        if (rawStatus == TaskRunState.SUCCEEDED) return "COMPLETED";
        return rawStatus.name();
    }

    private JobModule module(TaskRun run) {
        return switch (run.getTaskType()) {
            case IDEA_ATTACHMENT_PARSE, IDEA_BRIEF_DERIVATION -> JobModule.IDEA;
            case CONCEPT_PORTFOLIO_V2_RUN, CONCEPT_PORTFOLIO_V2_CONTINUE,
                CONCEPT_PORTFOLIO_V2_SELECTION_ACTION -> JobModule.CONCEPT_PORTFOLIO;
            case CONCEPT_FACTORY_RUN, CONCEPT_CANDIDATE, CONCEPT_DISTINCTNESS_JUDGE,
                CONCEPT_LEGAL_REVIEW, CONCEPT_REDESIGN -> JobModule.CONCEPT_FACTORY;
            case CONCEPT_HYPOTHESIS_ALTERNATIVE, CONCEPT_DELTA_LEGAL_REVIEW -> JobModule.CONCEPT_SELECTION;
            case TECH_OPS_PROPOSAL, TECH_OPS_ADVISORY -> JobModule.TECH_OPS;
            case FINANCE_ESTIMATE, FINANCE_ANALYSIS_REPORT -> JobModule.FINANCE;
            case LAUNCH_TECHNOLOGY_READINESS, LAUNCH_OPERATIONS_READINESS -> JobModule.LAUNCH_READINESS;
            case MARKETING_CONTENT_GENERATION, MARKETING_VISUAL_GENERATION -> JobModule.MARKETING;
            case MARKET_RESEARCH -> "MARKET_RESEARCH_BM".equals(run.getSubjectType())
                ? JobModule.BUSINESS_MODEL : JobModule.MARKET;
            case TWIN_SURVEY, TWIN_STIMULUS_DRAFT -> JobModule.TWIN;
        };
    }

    private enum JobModule {
        IDEA("/idea"), CONCEPT_PORTFOLIO("/concepts"), CONCEPT_FACTORY("/concepts"), CONCEPT_SELECTION("/concepts/compare"),
        MARKET("/market"), BUSINESS_MODEL("/business-model"), TWIN("/twin-survey"),
        TECH_OPS("/tech-ops"), FINANCE("/finance"), LAUNCH_READINESS("/launch-readiness"), MARKETING("/marketing");
        private final String route;
        JobModule(String route) { this.route = route; }
    }
}
