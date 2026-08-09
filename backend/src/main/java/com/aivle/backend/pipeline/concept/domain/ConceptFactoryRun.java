package com.aivle.backend.pipeline.concept.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_factory_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptFactoryRun extends BaseEntity {
    private static final Map<ConceptFactoryRunStatus, Set<ConceptFactoryRunStatus>> TRANSITIONS = Map.of(
        ConceptFactoryRunStatus.QUEUED, EnumSet.of(ConceptFactoryRunStatus.GENERATING, ConceptFactoryRunStatus.FAILED, ConceptFactoryRunStatus.STALE),
        ConceptFactoryRunStatus.GENERATING, EnumSet.of(ConceptFactoryRunStatus.VALIDATING, ConceptFactoryRunStatus.NEEDS_INPUT, ConceptFactoryRunStatus.FAILED, ConceptFactoryRunStatus.STALE),
        ConceptFactoryRunStatus.VALIDATING, EnumSet.of(ConceptFactoryRunStatus.REPLACING, ConceptFactoryRunStatus.NEEDS_INPUT, ConceptFactoryRunStatus.COMPLETED, ConceptFactoryRunStatus.FAILED, ConceptFactoryRunStatus.STALE),
        ConceptFactoryRunStatus.REPLACING, EnumSet.of(ConceptFactoryRunStatus.GENERATING, ConceptFactoryRunStatus.NEEDS_INPUT, ConceptFactoryRunStatus.FAILED, ConceptFactoryRunStatus.STALE),
        ConceptFactoryRunStatus.NEEDS_INPUT, EnumSet.of(ConceptFactoryRunStatus.QUEUED, ConceptFactoryRunStatus.FAILED, ConceptFactoryRunStatus.STALE),
        ConceptFactoryRunStatus.FAILED, EnumSet.of(ConceptFactoryRunStatus.QUEUED, ConceptFactoryRunStatus.STALE)
    );

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "source_idea_brief_snapshot_id", nullable = false, length = 64)
    private String sourceIdeaBriefSnapshotId;

    @Column(name = "source_snapshot_hash", nullable = false, length = 71)
    private String sourceSnapshotHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConceptFactoryRunStatus status;

    @Column(nullable = false)
    private int replacementRounds;

    @Column(nullable = false)
    private int inspectedCandidateCount;

    @Column(nullable = false)
    private int providerTransientRetryCount;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(length = 64)
    private String taskRunId;

    @Column(length = 128)
    private String lastRetryIdempotencyKey;

    public static ConceptFactoryRun create(Project project, String snapshotId, String snapshotHash, Long userId) {
        if (project == null || snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("confirmed Idea Brief snapshot is required");
        if (snapshotHash == null || !snapshotHash.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("snapshot hash is invalid");
        ConceptFactoryRun run = new ConceptFactoryRun();
        run.id = UUID.randomUUID().toString();
        run.project = project;
        run.sourceIdeaBriefSnapshotId = snapshotId;
        run.sourceSnapshotHash = snapshotHash;
        run.status = ConceptFactoryRunStatus.QUEUED;
        run.createdByUserId = userId;
        return run;
    }

    public void attachTaskRun(String taskRunId) {
        if (this.taskRunId != null && !this.taskRunId.equals(taskRunId)) throw new IllegalStateException("task run already attached");
        this.taskRunId = taskRunId;
    }

    public boolean retryReplay(String key) {
        return key != null && key.equals(lastRetryIdempotencyKey);
    }

    public void attachRetryTaskRun(String taskRunId, String key) {
        if (taskRunId == null || taskRunId.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("retry task run and idempotency key are required");
        }
        this.taskRunId = taskRunId;
        this.lastRetryIdempotencyKey = key;
    }

    public void transitionTo(ConceptFactoryRunStatus next) {
        if (next == status) return;
        if (!TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("invalid Concept Factory run transition: " + status + " -> " + next);
        }
        status = next;
    }

    public void recordCandidateInspection() {
        if (inspectedCandidateCount >= ConceptFactoryLimits.MAX_INSPECTED_CANDIDATES) {
            throw new IllegalStateException("INSPECTION_BUDGET_EXHAUSTED");
        }
        inspectedCandidateCount++;
    }

    public void beginReplacementRound() {
        ensureReplacementRound(replacementRounds + 1);
    }

    public void ensureReplacementRound(int round) {
        if (round < 1 || round > ConceptFactoryLimits.MAX_REPLACEMENT_ROUNDS) throw new IllegalStateException("replacement round limit exceeded");
        if (round <= replacementRounds) return;
        replacementRounds = round;
        transitionTo(ConceptFactoryRunStatus.REPLACING);
    }

    public void recordProviderTransientRetry() {
        providerTransientRetryCount++;
    }

    public boolean isTerminal() {
        return status == ConceptFactoryRunStatus.NEEDS_INPUT || status == ConceptFactoryRunStatus.COMPLETED
            || status == ConceptFactoryRunStatus.FAILED || status == ConceptFactoryRunStatus.STALE;
    }
}
