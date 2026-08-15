package com.aivle.backend.pipeline.conceptportfolio.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.idea.domain.IdeaBrief;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_portfolio_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptPortfolioRun extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_idea_brief_snapshot_id", nullable = false) private IdeaBrief sourceIdeaBrief;
    @Column(nullable = false, length = 71) private String sourceSnapshotHash;
    @Column(nullable = false) private int requestedMaxConcepts;
    @Column(nullable = false) private int producedConceptCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ConceptPortfolioRunStatus productStatus;
    @Column(length = 64) private String engineRunId;
    @Column(length = 40) private String engineStatus;
    @Column(length = 40) private String runtimeStage;
    @Column(length = 80) private String downstreamReadiness;
    @Column(length = 64) private String initialTaskRunId;
    @Column(length = 64) private String activeTaskRunId;
    @Column(length = 200) private String engineDefaultCandidateId;
    @Column(length = 100) private String resultContract;
    @Column(length = 20) private String resultSchemaVersion;
    @Column(columnDefinition = "TEXT") private String resultSnapshotJson;
    @Column(length = 80) private String failureCode;
    @Column(nullable = false) private int openInputCount;
    @Column(nullable = false, length = 71) private String requestHash;
    @Column(nullable = false, length = 128) private String idempotencyKey;
    @Column(nullable = false) private Long createdByUserId;
    @Column(nullable = false) private boolean isCurrent;

    public static ConceptPortfolioRun queued(Project project, IdeaBrief source, int requestedMaximum,
            String requestHash, String idempotencyKey, Long userId) {
        if (project == null || source == null || !source.isConfirmed()
                || !source.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("confirmed Idea Brief must belong to the project");
        }
        if (requestedMaximum < 1 || requestedMaximum > 5
                || requestHash == null || !requestHash.matches("sha256:[0-9a-f]{64}")
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
                || userId == null) {
            throw new IllegalArgumentException("Concept Portfolio run request is invalid");
        }
        ConceptPortfolioRun value = new ConceptPortfolioRun();
        value.id = UUID.randomUUID().toString();
        value.project = project;
        value.sourceIdeaBrief = source;
        value.sourceSnapshotHash = source.getSnapshotHash();
        value.requestedMaxConcepts = requestedMaximum;
        value.productStatus = ConceptPortfolioRunStatus.QUEUED;
        value.requestHash = requestHash;
        value.idempotencyKey = idempotencyKey;
        value.createdByUserId = userId;
        value.isCurrent = true;
        return value;
    }

    public void attachInitialTask(String taskRunId) {
        if (taskRunId == null || taskRunId.isBlank()) throw new IllegalArgumentException("task run is required");
        initialTaskRunId = taskRunId;
        activeTaskRunId = taskRunId;
    }

    public void markRunning() {
        if (productStatus == ConceptPortfolioRunStatus.RUNNING) return;
        if (productStatus != ConceptPortfolioRunStatus.QUEUED) {
            throw new IllegalStateException("Portfolio run is not queued");
        }
        productStatus = ConceptPortfolioRunStatus.RUNNING;
    }

    public void materialize(ConceptPortfolioRunStatus status, int produced, int openInputs,
            String engineRunId, String engineStatus, String runtimeStage, String downstreamReadiness,
            String engineDefaultCandidateId, String resultContract, String resultSchemaVersion,
            String resultSnapshotJson, String failureCode) {
        if (status == null || status == ConceptPortfolioRunStatus.QUEUED
                || status == ConceptPortfolioRunStatus.RUNNING || status == ConceptPortfolioRunStatus.STALE
                || produced < 0 || produced > 5 || produced > requestedMaxConcepts || openInputs < 0) {
            throw new IllegalArgumentException("Portfolio materialization state is invalid");
        }
        this.productStatus = status;
        this.producedConceptCount = produced;
        this.openInputCount = openInputs;
        this.engineRunId = engineRunId;
        this.engineStatus = engineStatus;
        this.runtimeStage = runtimeStage;
        this.downstreamReadiness = downstreamReadiness;
        this.engineDefaultCandidateId = engineDefaultCandidateId;
        this.resultContract = resultContract;
        this.resultSchemaVersion = resultSchemaVersion;
        this.resultSnapshotJson = resultSnapshotJson;
        this.failureCode = failureCode;
        this.activeTaskRunId = null;
    }

    public void markFailed(String failureCode) {
        productStatus = ConceptPortfolioRunStatus.FAILED;
        this.failureCode = failureCode;
        activeTaskRunId = null;
    }

    public void markStale() {
        if (productStatus == ConceptPortfolioRunStatus.QUEUED
                || productStatus == ConceptPortfolioRunStatus.RUNNING) {
            throw new IllegalStateException("active Portfolio run cannot be superseded");
        }
        productStatus = ConceptPortfolioRunStatus.STALE;
        isCurrent = false;
    }

    public void attachContinuationTask(String taskRunId) {
        if (taskRunId == null || taskRunId.isBlank() || activeTaskRunId != null
                || productStatus == ConceptPortfolioRunStatus.STALE) {
            throw new IllegalStateException("Portfolio continuation cannot be attached");
        }
        activeTaskRunId = taskRunId;
        if (producedConceptCount == 0) productStatus = ConceptPortfolioRunStatus.RUNNING;
    }

    public void completeContinuation(ConceptPortfolioRunStatus status, int produced, int openInputs,
            String failureCode) {
        if (status == null || status == ConceptPortfolioRunStatus.QUEUED
                || status == ConceptPortfolioRunStatus.RUNNING || status == ConceptPortfolioRunStatus.STALE
                || produced < 0 || produced > requestedMaxConcepts || openInputs < 0) {
            throw new IllegalArgumentException("Portfolio continuation state is invalid");
        }
        productStatus = status;
        producedConceptCount = produced;
        openInputCount = openInputs;
        this.failureCode = failureCode;
        activeTaskRunId = null;
    }

    public void continuationFailed(int produced, int openInputs) {
        producedConceptCount = produced;
        openInputCount = openInputs;
        productStatus = produced > 0
            ? (openInputs > 0 ? ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT
                : ConceptPortfolioRunStatus.RESULTS_AVAILABLE)
            : ConceptPortfolioRunStatus.NEEDS_INPUT;
        activeTaskRunId = null;
    }
}
