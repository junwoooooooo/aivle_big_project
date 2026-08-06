package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.brief.OpportunityBriefVersionRepository;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.journey.foundation.SnapshotHasher;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegulatoryBoundaryService {
    private final FoundationProjectAccess projectAccess;
    private final OpportunityBriefVersionRepository briefs;
    private final TaskRunRepository taskRuns;
    private final RegulatoryBoundaryRunRepository runs;
    private final RegulatoryBoundaryVersionRepository versions;
    private final SnapshotHasher hasher;

    public RegulatoryBoundaryService(FoundationProjectAccess projectAccess,
            OpportunityBriefVersionRepository briefs, TaskRunRepository taskRuns,
            RegulatoryBoundaryRunRepository runs, RegulatoryBoundaryVersionRepository versions,
            SnapshotHasher hasher) {
        this.projectAccess = projectAccess;
        this.briefs = briefs;
        this.taskRuns = taskRuns;
        this.runs = runs;
        this.versions = versions;
        this.hasher = hasher;
    }

    @Transactional
    public RegulatoryBoundaryRun createRun(Long ownerId, Long projectId, Long briefVersionId,
            String taskRunId) {
        Project project = projectAccess.requireOwned(ownerId, projectId);
        OpportunityBriefVersion brief = briefs.findByIdAndProjectIdAndDeletedAtIsNull(briefVersionId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        TaskRun taskRun = taskRunId == null ? null : taskRuns.findOwned(ownerId, projectId, taskRunId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return runs.save(RegulatoryBoundaryRun.queued(project, brief, taskRun, brief.getSnapshotHash()));
    }

    @Transactional
    public RegulatoryBoundaryRun start(Long ownerId, Long projectId, Long runId) {
        projectAccess.requireOwned(ownerId, projectId);
        RegulatoryBoundaryRun run = ownedRun(projectId, runId);
        run.start();
        return run;
    }

    @Transactional
    public RegulatoryBoundaryRun succeed(Long ownerId, Long projectId, Long runId) {
        projectAccess.requireOwned(ownerId, projectId);
        RegulatoryBoundaryRun run = ownedRun(projectId, runId);
        run.succeed(LocalDateTime.now());
        return run;
    }

    @Transactional
    public RegulatoryBoundaryVersion createVersion(Long ownerId, Long projectId, Long runId,
            RegulatoryBoundaryVersion.Status status, String snapshotJson) {
        projectAccess.requireOwnedForUpdate(ownerId, projectId);
        RegulatoryBoundaryRun run = ownedRun(projectId, runId);
        int nextVersion = versions.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId)
            .map(value -> value.getVersionNumber() + 1).orElse(1);
        return versions.save(RegulatoryBoundaryVersion.create(
            run, nextVersion, status, snapshotJson, hasher.hash(snapshotJson)));
    }

    @Transactional(readOnly = true)
    public RegulatoryBoundaryVersion current(Long ownerId, Long projectId) {
        projectAccess.requireOwned(ownerId, projectId);
        return versions.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId).orElse(null);
    }

    private RegulatoryBoundaryRun ownedRun(Long projectId, Long runId) {
        return runs.findByIdAndProjectIdAndDeletedAtIsNull(runId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
