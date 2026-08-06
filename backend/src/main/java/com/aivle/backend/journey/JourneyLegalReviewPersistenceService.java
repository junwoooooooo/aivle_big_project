package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JourneyLegalReviewPersistenceService {
    private final LegalReviewRunRepository runs;
    private final IdeaSourceRepository sources;
    private final IdeaVersionRepository versions;
    private final ProjectRepository projects;
    private final TaskRunRepository taskRuns;

    public JourneyLegalReviewPersistenceService(LegalReviewRunRepository runs, IdeaSourceRepository sources,
            IdeaVersionRepository versions, ProjectRepository projects, TaskRunRepository taskRuns) {
        this.runs = runs;
        this.sources = sources;
        this.versions = versions;
        this.projects = projects;
        this.taskRuns = taskRuns;
    }

    @Transactional
    public void markRunning(Long ownerId, Long projectId, Long runId, String taskRunId,
            Long expectedIdeaVersionId) {
        Project project = projects.findByIdForUpdate(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
        IdeaVersion currentVersion = versions.findCurrent(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        Long currentSourceId = sources.findCurrent(projectId).map(IdeaSource::getId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        if (!project.getOwner().getId().equals(ownerId)
            || !currentVersion.isConfirmed()
            || !currentVersion.getId().equals(expectedIdeaVersionId)
            || !currentVersion.getSource().getId().equals(currentSourceId)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }

        LegalReviewRun run = locked(runId);
        if ((run.getState() != LegalReviewRun.State.PENDING && run.getState() != LegalReviewRun.State.FAILED)
            || !run.getIdeaVersion().getId().equals(expectedIdeaVersionId)) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        TaskRun taskRun = taskRuns.findLocked(taskRunId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        run.start(taskRun);
    }

    @Transactional
    public void complete(Long runId, LegalReviewRun.LegalStatus status, String resultJson) {
        LegalReviewRun run = locked(runId);
        if (run.getState() == LegalReviewRun.State.SUCCEEDED) return;
        boolean adoptedTaskResult = run.getTaskRun() != null
            && run.getTaskRun().getState() == TaskRunState.SUCCEEDED
            && run.getTaskRun().getFinalResultId() != null;
        if (run.getState() != LegalReviewRun.State.RUNNING && !adoptedTaskResult) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        run.succeed(status, resultJson);
    }

    @Transactional
    public void fail(Long runId) {
        LegalReviewRun run = locked(runId);
        if (run.getState() == LegalReviewRun.State.PENDING || run.getState() == LegalReviewRun.State.RUNNING) {
            run.fail();
        }
    }

    private LegalReviewRun locked(Long runId) {
        return runs.findLockedById(runId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
