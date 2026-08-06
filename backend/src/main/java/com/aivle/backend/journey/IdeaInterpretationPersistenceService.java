package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaInterpretationPersistenceService {
    private final IdeaInterpretationRunRepository runs;
    private final IdeaVersionRepository versions;
    private final TaskRunRepository taskRuns;
    private final ProjectRepository projects;
    private final IdeaSourceRepository sources;
    private final IdeaOriginService origins;
    private final tools.jackson.databind.ObjectMapper mapper;

    public IdeaInterpretationPersistenceService(IdeaInterpretationRunRepository runs,
            IdeaVersionRepository versions, TaskRunRepository taskRuns, ProjectRepository projects,
            IdeaSourceRepository sources, IdeaOriginService origins, tools.jackson.databind.ObjectMapper mapper) {
        this.runs = runs;
        this.versions = versions;
        this.taskRuns = taskRuns;
        this.projects = projects;
        this.sources = sources;
        this.origins = origins;
        this.mapper = mapper;
    }

    @Transactional
    public void markRunning(Long ownerId, Long projectId, Long runId, String taskRunId, Long expectedSourceId) {
        Project project = projects.findByIdForUpdate(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        Long currentSourceId = sources.findCurrent(projectId).map(IdeaSource::getId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        if (!project.getOwner().getId().equals(ownerId) || !currentSourceId.equals(expectedSourceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        IdeaInterpretationRun run = locked(runId);
        if (run.getState() != IdeaInterpretationRun.State.PENDING
            || !run.getSource().getId().equals(expectedSourceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        TaskRun taskRun = taskRuns.findLocked(taskRunId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        run.start(taskRun);
    }

    @Transactional
    public void complete(Long runId, ParsedIdeaResult result) {
        IdeaInterpretationRun run = locked(runId);
        if (run.getState() == IdeaInterpretationRun.State.SUCCEEDED) return;
        boolean adoptedTaskResult = run.getTaskRun() != null
            && run.getTaskRun().getState() == TaskRunState.SUCCEEDED
            && run.getTaskRun().getFinalResultId() != null;
        if (run.getState() != IdeaInterpretationRun.State.RUNNING && !adoptedTaskResult) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        IdeaVersion version = versions
            .findTopBySourceIdAndDeletedAtIsNullOrderByVersionNumberDesc(run.getSource().getId())
            .orElseGet(() -> versions.save(IdeaVersion.create(
                run.getProject(), run.getSource(),
                Math.toIntExact(versions.countByProjectIdAndDeletedAtIsNull(run.getProject().getId()) + 1),
                result.normalizedDescription(), result.facts(), result.assumptions(),
                result.constraints(), result.openQuestions(), result.readiness()
            )));
        if (!version.getSource().getId().equals(run.getSource().getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        origins.createDraft(run.getProject(), run.getSource(), version, mapper.readTree(result.resultJson()));
        run.succeed(result.resultJson());
    }

    @Transactional
    public void fail(Long runId, String code) {
        IdeaInterpretationRun run = locked(runId);
        if (run.getState() == IdeaInterpretationRun.State.PENDING
            || run.getState() == IdeaInterpretationRun.State.RUNNING) {
            run.fail(code);
        }
    }

    private IdeaInterpretationRun locked(Long runId) {
        return runs.findLockedById(runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public record ParsedIdeaResult(String resultJson, String normalizedDescription, String facts,
        String assumptions, String constraints, String openQuestions, IdeaVersion.Readiness readiness) { }
}
