package com.aivle.backend.pipeline.marketinterview;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_interview_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketInterviewRun extends BaseEntity {
    public enum State { RUNNING, SUCCEEDED, FAILED, STALE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "task_run_id", nullable = false) private TaskRun taskRun;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64)
    private String sourceMarketSeedSnapshotId;
    @Column(name = "source_selection_id", nullable = false) private Long sourceSelectionId;
    @Column(name = "source_selection_revision", nullable = false) private int sourceSelectionRevision;
    @Column(name = "source_bm_plan_revision", nullable = false) private int sourceBmPlanRevision;
    @Column(nullable = false) private int attempt;
    @Column(nullable = false, length = 128) private String idempotencyKey;
    @Column(name = "input_hash", nullable = false, length = 71) private String inputHash;
    @Column(columnDefinition = "TEXT") private String resultJson;
    @Column(length = 80) private String failureCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state;
    @Column(nullable = false) private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static MarketInterviewRun create(Project project, TaskRun taskRun, String seedId,
            Long selectionId, int selectionRevision, int bmPlanRevision, int attempt, String idempotencyKey,
            String inputHash, LocalDateTime now) {
        if (project == null || taskRun == null || blank(seedId) || selectionId == null
                || selectionRevision < 0 || bmPlanRevision < 0 || attempt < 1 || attempt > 3 || blank(idempotencyKey)
                || !hash(inputHash) || now == null) throw new IllegalArgumentException("Market interview run is invalid");
        MarketInterviewRun value = new MarketInterviewRun();
        value.project = project; value.taskRun = taskRun; value.sourceMarketSeedSnapshotId = seedId;
        value.sourceSelectionId = selectionId; value.sourceSelectionRevision = selectionRevision;
        value.sourceBmPlanRevision = bmPlanRevision;
        value.attempt = attempt; value.idempotencyKey = idempotencyKey; value.inputHash = inputHash;
        value.state = State.RUNNING; value.startedAt = now;
        return value;
    }

    public void succeed(String resultJson, LocalDateTime now) {
        if (state == State.STALE) return;
        this.resultJson = resultJson; this.failureCode = null; this.state = State.SUCCEEDED; this.completedAt = now;
    }

    public void fail(String failureCode, LocalDateTime now) {
        if (state == State.SUCCEEDED || state == State.STALE) return;
        this.failureCode = blank(failureCode) ? "EXECUTION_FAILED" : failureCode;
        this.state = State.FAILED; this.completedAt = now;
    }

    public void markStale(String historicalResultJson, LocalDateTime now) {
        if (historicalResultJson != null) this.resultJson = historicalResultJson;
        this.state = State.STALE;
        if (completedAt == null) completedAt = now;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean hash(String value) { return value != null && value.matches("sha256:[0-9a-f]{64}"); }
}
