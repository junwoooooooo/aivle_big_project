package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable, source-bound proposal generation cursor. */
@Entity
@Table(name = "concept_refinement_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptRefinementRound extends BaseEntity {
    public enum State {
        PROPOSING, AWAITING_DECISION, NO_CHANGES, FAILED, STALE,
        DECISION_RECORDED, KEEP_CURRENT
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "selection_id", nullable = false) private Long selectionId;
    @Column(name = "business_validation_session_id", nullable = false, length = 64)
    private String businessValidationSessionId;
    @Column(name = "source_market_version_id", nullable = false) private Long sourceMarketVersionId;
    @Column(name = "source_bm_version_id", nullable = false) private Long sourceBmVersionId;
    @Column(name = "source_market_seed_snapshot_id", nullable = false, length = 64)
    private String sourceMarketSeedSnapshotId;
    @Column(name = "source_selection_revision", nullable = false) private Integer sourceSelectionRevision;
    @Column(name = "source_bm_plan_revision", nullable = false) private Integer sourceBmPlanRevision;
    @Column(name = "round_number", nullable = false) private int roundNumber;
    @Column(name = "policy_version", nullable = false, length = 40) private String policyVersion;
    @Column(name = "task_run_id", nullable = false, length = 64) private String taskRunId;
    @Column(nullable = false) private int attempt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private State state;
    @Column(name = "command_idempotency_key", nullable = false, length = 128)
    private String commandIdempotencyKey;
    @Column(name = "canonical_material_hash", nullable = false, length = 71)
    private String canonicalMaterialHash;
    @Column(name = "proposal_json", columnDefinition = "TEXT") private String proposalJson;
    @Column(name = "drift_rejections_json", columnDefinition = "TEXT") private String driftRejectionsJson;
    @Column(name = "last_error_code", length = 80) private String lastErrorCode;
    @Column(name = "decision_json", columnDefinition = "TEXT") private String decisionJson;
    @Column(name = "decision_hash", length = 71) private String decisionHash;
    @Column(name = "decision_idempotency_key", length = 128) private String decisionIdempotencyKey;
    @Column(name = "decided_by_user_id") private Long decidedByUserId;
    @Column(name = "decided_at") private Instant decidedAt;

    public static ConceptRefinementRound start(Long projectId, CompletedSource source,
            String taskRunId, String commandKey, String canonicalMaterialHash) {
        ConceptRefinementRound value = new ConceptRefinementRound();
        value.projectId = projectId;
        value.selectionId = source.selectionId();
        value.businessValidationSessionId = source.businessValidationSessionId();
        value.sourceMarketVersionId = source.marketVersionId();
        value.sourceBmVersionId = source.bmVersionId();
        value.sourceMarketSeedSnapshotId = source.marketSeedSnapshotId();
        value.sourceSelectionRevision = source.selectionRevision();
        value.sourceBmPlanRevision = source.bmPlanRevision();
        value.roundNumber = 1;
        value.policyVersion = ConceptRefinementPolicy.VERSION;
        value.taskRunId = taskRunId;
        value.attempt = 1;
        value.state = State.PROPOSING;
        value.commandIdempotencyKey = commandKey;
        value.canonicalMaterialHash = canonicalMaterialHash;
        return value;
    }

    public void retry(String taskRunId, String commandKey, String canonicalMaterialHash) {
        if (state != State.FAILED || attempt >= ConceptRefinementPolicy.MAX_ATTEMPTS_PER_ROUND) {
            throw new IllegalStateException("Refinement retry is unavailable");
        }
        this.taskRunId = taskRunId;
        this.commandIdempotencyKey = commandKey;
        this.canonicalMaterialHash = canonicalMaterialHash;
        this.attempt += 1;
        this.state = State.PROPOSING;
        this.lastErrorCode = null;
    }

    public void materialize(String proposalJson, String driftRejectionsJson, boolean hasProposals) {
        if (state != State.PROPOSING) throw new IllegalStateException("Refinement is not proposing");
        this.proposalJson = proposalJson;
        this.driftRejectionsJson = driftRejectionsJson;
        this.lastErrorCode = null;
        this.state = hasProposals ? State.AWAITING_DECISION : State.NO_CHANGES;
    }

    public void fail(String safeErrorCode) {
        if (state == State.PROPOSING) {
            state = State.FAILED;
            lastErrorCode = safeErrorCode;
        }
    }

    public void markStale() { state = State.STALE; }

    public void recordDecision(String decisionJson, String decisionHash,
            String idempotencyKey, Long userId, Instant now, boolean keepCurrent) {
        if (state != State.AWAITING_DECISION || this.decisionJson != null) {
            throw new IllegalStateException("Refinement decision is unavailable");
        }
        if (decisionJson == null || decisionJson.isBlank()
                || decisionHash == null || !decisionHash.matches("sha256:[0-9a-f]{64}")
                || idempotencyKey == null || idempotencyKey.isBlank()
                || userId == null || now == null) {
            throw new IllegalArgumentException("Refinement decision is invalid");
        }
        this.decisionJson = decisionJson;
        this.decisionHash = decisionHash;
        this.decisionIdempotencyKey = idempotencyKey;
        this.decidedByUserId = userId;
        this.decidedAt = now;
        this.state = keepCurrent ? State.KEEP_CURRENT : State.DECISION_RECORDED;
    }

    public boolean boundTo(CompletedSource source) {
        return source != null
            && java.util.Objects.equals(businessValidationSessionId, source.businessValidationSessionId())
            && java.util.Objects.equals(sourceMarketVersionId, source.marketVersionId())
            && java.util.Objects.equals(sourceBmVersionId, source.bmVersionId())
            && java.util.Objects.equals(sourceMarketSeedSnapshotId, source.marketSeedSnapshotId())
            && java.util.Objects.equals(selectionId, source.selectionId())
            && java.util.Objects.equals(sourceSelectionRevision, source.selectionRevision())
            && java.util.Objects.equals(sourceBmPlanRevision, source.bmPlanRevision());
    }
}
