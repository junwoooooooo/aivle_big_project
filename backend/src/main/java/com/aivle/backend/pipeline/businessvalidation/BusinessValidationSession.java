package com.aivle.backend.pipeline.businessvalidation;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.market.MarketResearchRun;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable orchestration lineage. Market/BM result payloads remain in their existing versions. */
@Entity
@Table(name = "business_validation_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessValidationSession extends BaseEntity {

    public enum State {
        MARKET_RUNNING, MARKET_FAILED, MARKET_COMPLETED, BM_RUNNING, BM_FAILED, COMPLETED, STALE
    }

    @Id @Column(length = 64) private String id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64)
    private String sourceMarketSeedSnapshotId;
    @Column(name = "source_portfolio_selection_id", nullable = false)
    private Long sourcePortfolioSelectionId;
    @Column(name = "source_selection_revision") private Integer sourceSelectionRevision;
    @Column(name = "source_bm_plan_revision") private Integer sourceBmPlanRevision;
    @Column(name = "market_task_run_id", nullable = false, length = 64) private String marketTaskRunId;
    @Column(name = "market_version_id") private Long marketVersionId;
    @Column(name = "bm_task_run_id", length = 64) private String bmTaskRunId;
    @Column(name = "bm_version_id") private Long bmVersionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private State state;
    @Column(name = "canonical_input_hash", nullable = false, length = 71) private String canonicalInputHash;
    @Column(name = "command_idempotency_key", nullable = false, length = 128)
    private String commandIdempotencyKey;
    @Column(name = "bm_command_idempotency_key", length = 128) private String bmCommandIdempotencyKey;
    @Column(name = "bm_attempt", nullable = false) private int bmAttempt;

    public static BusinessValidationSession start(Project project, MarketResearchRun marketRun,
            int sourceBmPlanRevision, String commandIdempotencyKey) {
        BusinessValidationSession value = new BusinessValidationSession();
        value.id = UUID.randomUUID().toString();
        value.project = project;
        value.sourceMarketSeedSnapshotId = marketRun.getSourceMarketSeedSnapshotId();
        value.sourcePortfolioSelectionId = marketRun.getSourcePortfolioSelectionId();
        value.sourceSelectionRevision = marketRun.getSourceSelectionRevision();
        value.sourceBmPlanRevision = sourceBmPlanRevision;
        value.marketTaskRunId = marketRun.getTaskRun().getId();
        value.canonicalInputHash = marketRun.getInputSnapshotHash();
        value.commandIdempotencyKey = commandIdempotencyKey;
        value.state = State.MARKET_RUNNING;
        value.bmAttempt = 0;
        return value;
    }

    public void markStale() { state = State.STALE; }
    public void marketFailed() { if (state != State.STALE) state = State.MARKET_FAILED; }
    public void marketCompleted(Long versionId) {
        if (state == State.STALE) return;
        marketVersionId = versionId;
        state = State.MARKET_COMPLETED;
    }
    public void bmStarted(String taskRunId, String commandKey) {
        if (state == State.STALE) return;
        bmTaskRunId = taskRunId;
        bmCommandIdempotencyKey = commandKey;
        bmVersionId = null;
        bmAttempt += 1;
        state = State.BM_RUNNING;
    }
    public void bmFailed() { if (state != State.STALE) state = State.BM_FAILED; }
    public void completed(Long versionId) {
        if (state == State.STALE) return;
        bmVersionId = versionId;
        state = State.COMPLETED;
    }
}
